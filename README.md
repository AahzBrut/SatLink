# SatLink - утилита расчета графика сеансов связи для спутниковой группировки
---

## Использование (для работы необходим JDK 19):
```shell
java -jar Satlink.jar
```

## Параметры [application.properies]:
```
# Путь к каталогу с исходными графиками зон видимости
connectionSchedulesPath = DATA_Files/Facility2Constellation

# Маска файлов с исходными графиками зон видимости
connectionScheduleFileNameStart = Facility-

# Путь к каталогу с исходными графиками фотографирования
flybySchedulesPath = DATA_Files/Russia2Constellation

# Маска файлов с исходными графиками фотографирования
flybyScheduleFileNameStart = Russia-To-Satellite

# Путь к каталогу с выходными графиками связи
resultsPath = result

# Путь к каталогу со статистическими отчетами
statisticsPath = statistics

# Максимальный квант времени связи станции и спутника
timeStep = 245000

# Формат даты/времени для исходных и выходных файлов
mainDateTimePattern = d MMM uuuu HH:mm:ss.SSS

# Формат даты/времени для статистических отчетов
statisticsDateTimePattern = dd.MM.uuuu HH:mm:ss.SSS
```

## Используемый алгоритм
Т.к. скорость заполнения памяти спутников многократно превышает скорость передачи данных со спутников на наземные станции наблюдения - 
необходимо масимально использовать имеющиеся окна видимости спутников со станций набюдения.

- Исходные графики зон видимости и графики периодов фотографирования загружаются из исходных файлов;
- Из графика зон видимости формируется массив окон возможной передачи данных с разбиением длинных окон на кванты, размер которых задается в параметрах;
- Каждая запись с информацией об окне содержит следующие данные - станция, спутник, начало окна, конец окна
- Массив окон возможной передачи сортируется по дате/времени начала каждого окна;
- Далее в цикле для каждого окна расчитывается:
    - время возможного начала окна для текущего спутника (проверяется, что спутник в течении этого окна не работает с какой-либо станцией)
    - время возможного начала окна для текущей станции (проверяется, что станция в течении этого окна не работает с каким-либо спутником)
    - время, необходимое для передачи текущего накопленного массива изображений
- далее, если спутнику есть что передавать и максимальное время готовности спутника и станции не выходит за границы окна:
    -  в операции спутника (отдельный накапливаемый массив) идет запись о сеансе связи (для дальнейшего использования при расчете времени готовности спутника)
    -  в операции станции (отдельный накапливаемый массив) идет запись о сеансе связи (для дальнейшего использования при расчете времени готовности станции)
- После завершения цикла обработки всех окон массив операций станций содержит расчитанный график сеансов связи



