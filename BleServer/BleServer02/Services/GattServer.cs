using System;
using System.Collections.Generic;
using System.Text;
using System.Threading.Tasks;
using Windows.Devices.Bluetooth.GenericAttributeProfile;
using Windows.Devices.Bluetooth;
using Windows.Storage.Streams;
using System.Runtime.InteropServices.WindowsRuntime;
using System.Reflection.PortableExecutable;

namespace BleServer02.Services
{
    public class GattServer : IGattServer
    {
        private GattServiceProvider _gattServiceProvider;
        private readonly ILogger _logger;
        private readonly Guid _serviceId;
        private Dictionary<Guid, GattLocalCharacteristic> _characteristics = new Dictionary<Guid, GattLocalCharacteristic>();

        public delegate void GattChararteristicHandler(object myObject, CharacteristicEventArgs myArgs);

        public event GattChararteristicHandler OnCharacteristicWrite;

        public GattServer(Guid serviceId, ILogger logger)
        {
            _logger = logger;
            _serviceId = serviceId;
        }

        private async Task LogConnectionStatus(string status)
        {
            await _logger.LogMessageAsync(status);
        }

        public async Task Initialize()
        {
            var cellaGatService = await GattServiceProvider.CreateAsync(_serviceId);

            if (cellaGatService.Error == BluetoothError.RadioNotAvailable)
            {
                throw new Exception("BLE not enabled");
            }

            if (cellaGatService.Error == BluetoothError.Success)
            {
                _gattServiceProvider = cellaGatService.ServiceProvider;

                // Добавляем характеристику
                await AddDataExchangeCharacteristicAsync(new Guid("72563044-DB33-4692-A45D-C5212EEBABFA"), "Data Exchange");

                // Запускаем сервер
                Start();
            }

            _gattServiceProvider.AdvertisementStatusChanged += async (sender, args) =>
            {
                await _logger.LogMessageAsync(
                    sender.AdvertisementStatus == GattServiceProviderAdvertisementStatus.Started ?
                    "GATT сервер включен." :
                    "GATT сервер выключен.");
            };
        }

        public async Task<bool> AddDataExchangeCharacteristicAsync(Guid characteristicId, string userDescription)
        {
            await _logger.LogMessageAsync($"Adding data exchange characteristic: description: {userDescription}, guid: {characteristicId}");

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
                await _logger.LogMessageAsync("Adding data exchange characteristic failed");
                return false;
            }

            var localCharacteristic = characteristicResult.Characteristic;
            _characteristics[characteristicId] = localCharacteristic;

            localCharacteristic.WriteRequested += async (sender, args) =>
            {
                using (args.GetDeferral())
                {
                    var request = await args.GetRequestAsync();
                    if (request == null)
                    {
                        return;
                    }

                    using (var dataReader = DataReader.FromBuffer(request.Value))
                    {
                        var characteristicValue = dataReader.ReadString(request.Value.Length);
                        OnCharacteristicWrite?.Invoke(null, new CharacteristicEventArgs(localCharacteristic.Uuid, characteristicValue));
                    }

                    if (request.Option == GattWriteOption.WriteWithResponse)
                    {
                        request.Respond();
                    }
                }
            };

            localCharacteristic.ReadRequested += async (sender, args) =>
            {
                var deferral = args.GetDeferral();
                var request = await args.GetRequestAsync();
                var writer = new DataWriter();
                writer.WriteString("Data to send to client");
                await _logger.LogMessageAsync("Read request received");
                request.RespondWithValue(writer.DetachBuffer());
                deferral.Complete();
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
            if (_characteristics.TryGetValue(characteristicId, out var characteristic))
            {
                var writer = new DataWriter();
                writer.WriteBytes(data);
                var buffer = writer.DetachBuffer();

                var notificationResults = await characteristic.NotifyValueAsync(buffer);

                // Проверяем каждый результат уведомления
                bool success = true;
                foreach (var result in notificationResults)
                {
                    if (result.Status != GattCommunicationStatus.Success)
                    {
                        success = false;
                        await _logger.LogMessageAsync($"Failed to send data: {result.Status}");
                    }
                }

                if (success)
                {
                    //await _logger.LogMessageAsync("Data successfully sent to client.");
                }
                return success;
            }
            else
            {
                await _logger.LogMessageAsync("Characteristic not found.");
                return false;
            }
        }


        public async void Stop()
        {
            _gattServiceProvider.StopAdvertising();
            await _logger.LogMessageAsync("GATT server stopped.");
        }
    }
}
