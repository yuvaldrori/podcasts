sed -i 's/org.gradle.jvmargs=-Xmx4096m -XX:MaxMetaspaceSize=1024m -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8/org.gradle.jvmargs=-Xmx6g -XX:MaxMetaspaceSize=1024m -XX:+HeapDumpOnOutOfMemoryError -XX:+UseParallelGC -Dfile.encoding=UTF-8/' gradle.properties
# AGP 9.0 defaults these to true, but explicitly setting them guarantees optimization
echo "android.nonTransitiveRClass=true" >> gradle.properties
echo "android.enableAppCompileTimeRClass=true" >> gradle.properties
