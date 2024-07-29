using System.Threading.Tasks;

namespace BleServer02
{
    public interface ILogger
    {
        Task LogMessageAsync(string message);
    }
}