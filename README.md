# Dynamic Module Availability

This extension, Dynamic Module Availability, empowers administrators within Brightspot to configure, on a per-site basis, a curated list of modules accessible for each content type, seamlessly organizing them into distinct style groups for enhanced customization.

* [Prerequisites](#prerequisites)
* [Installation](#installation)
* [Usage](#usage)
* [Documentation](#documentation)
* [Versioning](#versioning)
* [Contributing](#contributing)
* [Local Development](#local-development)
* [License](#license)

## Prerequisites

This extension requires an instance of [Brightspot](https://www.brightspot.com/) (version 4.5.15 or later) and access to the project source code.

## Installation

Gradle:
```groovy
api 'com.brightspot:dynamic-module-availability:1.0.0'
```

Maven:
```xml
<dependency>
    <groupId>com.brightspot</groupId>
    <artifactId>dynamic-module-availability</artifactId>
    <version>1.0.0</version>
</dependency>
```

Substitute `1.0.0` for the desired version found on the [releases](/releases) list.

## Usage

To opt in to this behavior, replace the `StyleEmbeddedContentCreator` class in any `@ToolUi.EmbeddedContentCreatorClass(StyleEmbeddedContentCreator.class)` annotations with the new content creator class `StyleGroupEmbeddedContentCreator`:

```java
public class MyContentType extends Content {

    @ToolUi.EmbeddedContentCreatorClass(StyleGroupEmbeddedContentCreator.class)
    private List<ModuleType> contents;
}
```

Now, while editing the contents field on `MyContentType`, users will encounter the innovative Embedded Content Creator, revealing only the modules configured in the respective sites and settings.

## Documentation

- [Javadocs](https://artifactory.psdops.com/public/com/brightspot/dynamic-module-availability/1.0.0-SNAPSHOT/dynamic-module-availability-1.0.0-20240122.204603-6-javadoc.jar!/index.html)

## Versioning

The version numbers for this extension will strictly follow [Semantic Versioning](https://semver.org/).

## Contributing

If you have feedback, suggestions or comments on this open-source platform extension, please feel free to make them publicly on the issues tab [here](https://github.com/brightspot/content-review-cycle/issues).

Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

## Local Development

Assuming you already have a local Brightspot instance up and running, you can 
test this extension by running the following command from this project's root 
directory to install a `SNAPSHOT` to your local Maven repository:

```shell
./gradlew publishToMavenLocal
```

Next, ensure your project's `build.gradle` file contains 

```groovy
repositories {
    mavenLocal()
}
```

Then, add the following to your project's `build.gradle` file:

```groovy
dependencies {
    api 'com.brightspot:dynamic-module-availability:1.0.0-SNAPSHOT'
}
```

Finally, compile your project and run your local Brightspot instance.

## License

See: [LICENSE](LICENSE).
