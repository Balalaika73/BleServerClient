using System;
using Windows.Devices.Bluetooth.GenericAttributeProfile;

namespace BleServer02.Services
{
    public class GattChararteristicHandler
    {
        public static Guid ServiceId = Guid.Parse("19536E67-3682-4588-9F3A-5340B6712150");

        // read-write characteristic -> exchange data
        public static Guid DataExchange = Guid.Parse("72563044-DB33-4692-A45D-C5212EEBABFA");
    }
}