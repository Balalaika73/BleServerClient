
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using Windows.UI.Core;
using Windows.UI.Xaml.Controls;

namespace BleServer02
{
    internal class ControlLogger : ILogger
    {
        private readonly TextBlock _textBox;

        public ControlLogger(TextBlock textBox)
        {
            _textBox = textBox;
        }

        public async Task LogMessageAsync(string message)
        {
            await _textBox.Dispatcher.RunAsync(CoreDispatcherPriority.Normal, () =>
            {
                _textBox.Text += message + Environment.NewLine;
            });
        }
    }
}
