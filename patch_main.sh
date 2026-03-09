#!/bin/bash
git checkout app/src/main/java/com/yuval/podcasts/MainActivity.kt
sed -i '/import android.os.Bundle/a import androidx.tracing.trace' app/src/main/java/com/yuval/podcasts/MainActivity.kt
sed -i 's/super.onCreate(savedInstanceState)/super.onCreate(savedInstanceState)\n        trace("MainActivity_onCreate") {/' app/src/main/java/com/yuval/podcasts/MainActivity.kt
sed -i 's/            }\n        }\n    }/            }\n        }\n        }\n    }/' app/src/main/java/com/yuval/podcasts/MainActivity.kt
