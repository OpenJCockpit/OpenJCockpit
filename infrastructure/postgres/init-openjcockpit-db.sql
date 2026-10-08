CREATE USER openjcockpit_app WITH PASSWORD 'openjcockpit_app';
CREATE DATABASE openjcockpit OWNER openjcockpit_app;
GRANT ALL PRIVILEGES ON DATABASE openjcockpit TO openjcockpit_app;
