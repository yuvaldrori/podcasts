#!/bin/bash
cat << 'INNER' > app/src/benchmark/AndroidManifest.xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- The benchmark build type enables profiling -->
    <application>
        <profileable android:shell="true" tools:targetApi="29" />
    </application>

</manifest>
INNER
