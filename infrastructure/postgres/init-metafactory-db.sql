CREATE USER metafactory_app WITH PASSWORD 'metafactory_app';
CREATE DATABASE metafactory OWNER metafactory_app;
GRANT ALL PRIVILEGES ON DATABASE metafactory TO metafactory_app;
