from .config import Settings
from .server import serve


serve(Settings.from_environment())
