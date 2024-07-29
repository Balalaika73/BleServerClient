using System.Threading.Tasks;
using System;

namespace BleServer02.Services
{
    public interface IGattServer
    {
        public delegate void GattChararteristicHandler(object myObject, CharacteristicEventArgs myArgs);
        Task Initialize();

        Task<bool> AddDataExchangeCharacteristicAsync(Guid characteristicId, string userDescription);
        Task<bool> SendNotificationAsync(Guid characteristicId, byte[] data);
        void Start();
        void Stop();
    }
}