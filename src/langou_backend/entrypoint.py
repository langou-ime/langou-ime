from langou_backend.config import Settings
from langou_backend.production import create_production_app

app = create_production_app(Settings())
