using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace BleServer02.Services
{
    public class CharacteristicEventArgs : EventArgs
    {
        public CharacteristicEventArgs(Guid characteristicId, object value)
        {
            Characteristic = characteristicId;
            Value = value;
        }

        public Guid Characteristic { get; set; }

        public object Value { get; set; }
    }
}
