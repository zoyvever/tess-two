./gradlew assembleDebug

# copy trained data
adb shell mkdir -p /data/local/tmp/micr/tessdata 
adb push micrtes/src/main/res/raw/mcr.traineddata /data/local/tmp/micr/tessdata/mcr.traineddata 

# copy, install & launch app
adb push /home/npakudin/Documents/tess-two-project/micrtes/build/outputs/apk/micrtes-debug.apk /data/local/tmp/com.example.micrtes 
adb shell pm install -r "/data/local/tmp/com.example.micrtes" 
adb shell am start -n "com.example.micrtes/com.example.micrtes.MainActivity" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER 


