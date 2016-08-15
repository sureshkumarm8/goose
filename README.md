Goose for Android
-----------------

Original Goose library (Scala, command line) is licensed by Gravity.com under the Apache 2.0 license, see the LICENSE file for more details.

Similar to the original library, this allows you to extract relevant information from a web URL, such as article text, title, description, important photo, etc.

About Android fork
------------------

This fork addresses the issues using Scala imposes on Android, such as using external programs (image-magick), using outdated HTTP libraries (Apache), downloading images to any location on the disk (no open disk on Android), managing cache, SD cards, battery consumption, network issues, redirects, etc.

Sample usage can be found in `DemoActivity.java` in the `app` folder's source.

Help wanted
-----------

This is still not very clean and nice. It works, but it is not clean. Some of the things to address:

* Comment code and fix javadoc issues. Some comments are... useless, obsolete or just boring
* Remove code that produces warnings; the base repo had a lot of them and almost none were fixed
* Remove the backport of the Apache dependency. This still works, but it needs to go
* Add a task to export the library to `jar` archive

Tip: to comment all logs use:

```find . -name "*\.java" | xargs grep -l 'Log\.' | xargs sed -i 's/Log\./;\/\/ Log\./g'```

Tip: to uncomment all logs use:

```find . -name "*\.java" | xargs grep -l 'Log\.' | xargs sed -i 's/;\/\/ Log\./Log\./g'```

Any help is appreciated.
