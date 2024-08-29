using System;
using System.Collections.Generic;
using System.Threading.Tasks;
using Windows.Devices.Bluetooth;
using Windows.Devices.Bluetooth.GenericAttributeProfile;
using Windows.Storage.Streams;

namespace BleServer02.Services
{
    public class GattServer : IGattServer
    {
        private GattServiceProvider _gattServiceProvider;
        private readonly ILogger _logger;
        private readonly Guid _serviceId;
        private readonly Dictionary<Guid, GattLocalCharacteristic> _characteristics = new Dictionary<Guid, GattLocalCharacteristic>();

        public event Action<object, CharacteristicEventArgs> OnCharacteristicWrite;

        private const int DELAY_MS = 60;
        private const int BLOCK_SIZE = 160;

        public GattServer(Guid serviceId, ILogger logger)
        {
            _logger = logger;
            _serviceId = serviceId;
        }

        public async Task Initialize()
        {
            var serviceResult = await GattServiceProvider.CreateAsync(_serviceId);

            if (serviceResult.Error == BluetoothError.RadioNotAvailable)
            {
                throw new Exception("BLE not enabled");
            }

            if (serviceResult.Error == BluetoothError.Success)
            {
                _gattServiceProvider = serviceResult.ServiceProvider;
                await AddDataExchangeCharacteristicAsync(new Guid("72563044-DB33-4692-A45D-C5212EEBABFA"), "Data Exchange");
                Start();
            }

            _gattServiceProvider.AdvertisementStatusChanged += async (sender, args) =>
            {
                var statusMessage = sender.AdvertisementStatus == GattServiceProviderAdvertisementStatus.Started
                    ? "GATT сервер включен."
                    : "GATT сервер выключен.";
                await _logger.LogMessageAsync(statusMessage);
            };
        }

        public async Task<bool> AddDataExchangeCharacteristicAsync(Guid characteristicId, string userDescription)
        {
            await _logger.LogMessageAsync($"Adding characteristic: {userDescription}, guid: {characteristicId}");

            var characteristicParameters = new GattLocalCharacteristicParameters
            {
                CharacteristicProperties = GattCharacteristicProperties.Notify | GattCharacteristicProperties.WriteWithoutResponse | GattCharacteristicProperties.Read,
                WriteProtectionLevel = GattProtectionLevel.Plain,
                ReadProtectionLevel = GattProtectionLevel.Plain,
                UserDescription = userDescription
            };

            var characteristicResult = await _gattServiceProvider.Service.CreateCharacteristicAsync(characteristicId, characteristicParameters);
            if (characteristicResult.Error != BluetoothError.Success)
            {
                await _logger.LogMessageAsync("Failed to add characteristic.");
                return false;
            }

            var localCharacteristic = characteristicResult.Characteristic;
            _characteristics[characteristicId] = localCharacteristic;

            localCharacteristic.WriteRequested += async (sender, args) =>
            {
                using (args.GetDeferral())
                {
                    var request = await args.GetRequestAsync();
                    if (request == null) return;

                    await Task.Delay(DELAY_MS);

                    using (var dataReader = DataReader.FromBuffer(request.Value))
                    {
                        var characteristicValue = dataReader.ReadString(request.Value.Length);
                        OnCharacteristicWrite?.Invoke(this, new CharacteristicEventArgs(localCharacteristic.Uuid, characteristicValue));
                    }

                    if (request.Option == GattWriteOption.WriteWithResponse)
                    {
                        request.Respond();
                    }
                }
            };

            localCharacteristic.ReadRequested += async (sender, args) =>
            {
                using (args.GetDeferral())
                {
                    var request = await args.GetRequestAsync();
                    var writer = new DataWriter();
                    writer.WriteString("Data to send to client");
                    request.RespondWithValue(writer.DetachBuffer());
                }

                await _logger.LogMessageAsync("Read request received");
            };

            return true;
        }

        public void Start()
        {
            if (_gattServiceProvider.AdvertisementStatus == GattServiceProviderAdvertisementStatus.Created ||
                _gattServiceProvider.AdvertisementStatus == GattServiceProviderAdvertisementStatus.Stopped)
            {
                var advParameters = new GattServiceProviderAdvertisingParameters
                {
                    IsDiscoverable = true,
                    IsConnectable = true
                };
                _gattServiceProvider.StartAdvertising(advParameters);
            }
        }

        public async Task<bool> SendNotificationAsync(Guid characteristicId, byte[] data)
        {
            if (!_characteristics.TryGetValue(characteristicId, out var characteristic))
            {
                await _logger.LogMessageAsync("Characteristic not found.");
                return false;
            }

            var blocks = SplitDataIntoBlocks(data, BLOCK_SIZE);
            var success = true;

            foreach (var block in blocks)
            {
                var writer = new DataWriter();
                writer.WriteBytes(block);
                var buffer = writer.DetachBuffer();

                var notificationResults = await characteristic.NotifyValueAsync(buffer);

                foreach (var result in notificationResults)
                {
                    if (result.Status != GattCommunicationStatus.Success)
                    {
                        success = false;
                        await _logger.LogMessageAsync($"Error sending data: {result.Status}");
                    }
                }

                await Task.Delay(DELAY_MS);
            }

            return success;
        }

        private static List<byte[]> SplitDataIntoBlocks(byte[] data, int blockSize)
        {
            var blocks = new List<byte[]>();
            var totalLength = data.Length;
            var offset = 0;

            while (offset < totalLength)
            {
                var length = Math.Min(blockSize, totalLength - offset);
                var block = new byte[length];
                Array.Copy(data, offset, block, 0, length);

                var excessBytes = isLastBytePartOfSymbol(block);
                if (excessBytes > 0 && length > excessBytes)
                {
                    length -= excessBytes;
                    block = new byte[length];
                    Array.Copy(data, offset, block, 0, length);
                }

                blocks.Add(block);
                offset += length;
            }

            return blocks;
        }

        private static int isLastBytePartOfSymbol(byte[] bytes)
        {
            if (bytes.Length == 0) return 0;

            var lastByte = bytes[bytes.Length - 1];
            if ((lastByte & 0x80) == 0) return 0;

            if ((lastByte & 0xC0) == 0x80)
            {
                for (int i = bytes.Length - 2; i >= 0; i--)
                {
                    var b = bytes[i];
                    if ((b & 0x80) == 0) return 0;
                    if ((b & 0xC0) == 0x80) continue;

                    if ((b & 0xE0) == 0xC0) return 2;
                    if ((b & 0xF0) == 0xE0) return 3;
                    if ((b & 0xF8) == 0xF0) return 4;
                }
            }

            return 1;
        }

        public async void Stop()
        {
            _gattServiceProvider.StopAdvertising();
            await _logger.LogMessageAsync("GATT server stopped.");
        }
    }
}
