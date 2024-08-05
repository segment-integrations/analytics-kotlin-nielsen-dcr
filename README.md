# Analytics-Kotlin Nielsen-DCR

Add Nielsen-DCR support to your applications via this plugin for [Analytics-Kotlin](https://github.com/segmentio/analytics-kotlin)

⚠️ **Github Issues disabled in this repository** ⚠️

Please direct all issues, bug reports, and feature enhancements to `friends@segment.com` so they can be resolved as efficiently as possible. 

## Adding the dependency
To install the Segment-Nielsen-DCR integration, simply add this line to your gradle file:

```
implementation 'com.segment.analytics.kotlin.destinations:nielsen-dcr:<latest_version>'
```

Or the following for Kotlin DSL

```
implementation("com.segment.analytics.kotlin.destinations:nielsen-dcr:<latest_version>")
```

Also add the Maven Nielsen Digital SDK repo (since Nielsen doesn’t publish it on Maven Central) inside repositories section in project level build.gradle.
```
allprojects {
    repositories {
        mavenCentral()
        maven {
            url 'https://raw.githubusercontent.com/NielsenDigitalSDK/nielsenappsdk-android/master/'
        }
    }
}
```
Or the following for Kotlin DSL
```
allprojects {
    repositories {
        mavenCentral()
        maven {
            url = uri("https://raw.githubusercontent.com/NielsenDigitalSDK/nielsenappsdk-android/master/")
        }
    }
}

```

## Using the Plugin in your App

Open the file where you setup and configure the Analytics-Kotlin library.  Add this plugin to the list of imports.

```
import com.segment.analytics.kotlin.destinations.nielsendcr.NielsenDCRDestination
```

Just under your Analytics-Kotlin library setup, call `analytics.add(plugin = ...)` to add an instance of the plugin to the Analytics timeline.

```
    analytics = Analytics("<YOUR WRITE KEY>", applicationContext) {
        this.flushAt = 3
        this.trackApplicationLifecycleEvents = true
    }
    analytics.add(plugin = NielsenDCRDestination())
```

Your events will now begin to flow to Nielsen-DCR in device mode.


## Integrating with Segment

Interested in integrating your service with us? Check out our [Partners page](https://segment.com/partners/) for more details.
Please see [our documentation](https://segment.com/docs/connections/destinations/catalog/nielsen-dcr/) for more information.


## Support

Please use Github issues, Pull Requests, or feel free to reach out to our [support team](https://segment.com/help/).


## License
```
MIT License

Copyright (c) 2021 Segment

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
