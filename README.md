- language: Java 11
- build tool: Maven
- test framework: testng
- UI test framework: Selenium


Co by šlo vylepšit
--

- Použít Page Objet pattern. Zvýšila by se čitelnost samotného testu. CSS a XPATH lokátory by byly na jednom místě. 
- napsat to v JavaScriptu či TypeScriptu :)


### Spuštění docker kontejneru se Selenium serverem a prohlížečem Chrome

`docker run -d -p 4444:4444 -p 5900:5900 -v /dev/shm:/dev/shm selenium/standalone-chrome-debug:3.141.59`