# Телеграмм бот для получения расписания игр NHL

[//]: # (## Запуск)
Запуск
======
### set TOKEN in .env file e.g. TOKEN=token1234 

    sbt docker:publishLocal
    docker-compose up
### Для запуска тестов
     sbt test

### Description
У бота есть три основных команды

- /today и /tmrw - получить расписание игра на сегодня или завтра. В полученных списаках можно сохранять игры
- /marked - получить список отмеченных игр