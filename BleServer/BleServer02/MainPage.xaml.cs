using BleServer02.Services;
using Microsoft.Extensions.Logging;
using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Runtime.InteropServices.WindowsRuntime;
using System.Text;
using System.Threading.Tasks;
using System.Xml.Linq;
using Windows.Devices.Bluetooth;
using Windows.Devices.Bluetooth.Advertisement;
using Windows.Devices.Bluetooth.GenericAttributeProfile;
using Windows.Foundation;
using Windows.Foundation.Collections;
using Windows.Storage.Streams;
using Windows.UI.Xaml;
using Windows.UI.Xaml.Controls;
using Windows.UI.Xaml.Controls.Primitives;
using Windows.UI.Xaml.Data;
using Windows.UI.Xaml.Input;
using Windows.UI.Xaml.Media;
using Windows.UI.Xaml.Navigation;

// Документацию по шаблону элемента "Пустая страница" см. по адресу https://go.microsoft.com/fwlink/?LinkId=402352&clcid=0x419

namespace BleServer02
{
    /// <summary>
    /// Пустая страница, которую можно использовать саму по себе или для перехода внутри фрейма.
    /// </summary>
    public sealed partial class MainPage : Page
    {
        private ILogger _logger;
        private GattServer _gattServer;


        public MainPage()
        {
            InitializeComponent();
            InitializeLogger();
            InitializeGattServer();
        }

        private void InitializeLogger()
        {
            _logger = new ControlLogger(LogTextBox);
        }

        private void InitializeGattServer()
        {
            _gattServer = new GattServer(GattChararteristicHandler.ServiceId, _logger);
            _gattServer.OnCharacteristicWrite += _gattServer_OnChararteristicWrite;
        }

        private async void _gattServer_OnChararteristicWrite(object myObject, CharacteristicEventArgs myArgs)
        {
            await _logger.LogMessageAsync($"Полученные данные: {myArgs.Value.ToString()}");
        }

        private async void StartGattServer_Click(object sender, RoutedEventArgs e)
        {
            try
            {
                await _gattServer.Initialize();
            }
            catch
            {
                return;
            }
            _gattServer.Start();
        }

        private async void StopGattServer_Click(object sender, RoutedEventArgs e)
        {
            _gattServer.Stop();
        }

        private async void btnSendData_Click(object sender, RoutedEventArgs e)
        {
            if (txtData.Text != null || txtData.Text != "")
            {
                byte[] dataToSend = Encoding.UTF8.GetBytes(txtData.Text);
                txtData.Text = "";

                bool success = await _gattServer.SendNotificationAsync(GattChararteristicHandler.DataExchange, dataToSend);

                if (success)
                {
                    await _logger.LogMessageAsync("Данные успешно отправлены.");
                }
                else
                {
                    await _logger.LogMessageAsync("Ошибка отправки данных.");
                }
            }
            else
                await _logger.LogMessageAsync("Введите данные для отправки.");
        }

    }
}
