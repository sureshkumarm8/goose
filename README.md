Goose for Android
-----------------

Original Goose library (Scala, command line) is licensed by Gravity.com under the Apache 2.0 license, see the LICENSE file for more details.

This fork addresses the issues using Scala imposes on Android, such as using external programs (image-magick), using outdated HTTP libraries (Apache), downloading images to any location on the disk (no open disk on Android), managing cache, SD cards, battery consumption, network issues, redirects, etc.

Work in progress
----------------

This is still being developed, to comment all logs use:

```find . -name "*\.java" | xargs grep -l 'Log\.' | xargs sed -i 's/Log\./;\/\/ Log\./g'```

And to uncomment all logs use:

```find . -name "*\.java" | xargs grep -l 'Log\.' | xargs sed -i 's/;\/\/ Log\./Log\./g'```

Any help is _appreciated_.


