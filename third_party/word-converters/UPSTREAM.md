# Word conversion dependencies

- Java Mammoth 1.13.0, BSD-2-Clause. The local `SimpleSax` class replaces only the XML factory setup with Apache Commons Secure XML for Android. The rest of Mammoth is pulled from Maven Central. [Upstream source](https://github.com/mwilliamson/java-mammoth)
- Apache Commons Secure XML 1.1.0 and Apache POI Scratchpad 5.5.1, Apache-2.0. DOC conversion uses POI's HWPF HTML converter. The StAX and desktop package relocations follow the POI Android sample. [Apache POI](https://poi.apache.org/components/document/) · [poi-on-android](https://github.com/centic9/poi-on-android)

The APK includes the license and notice texts in `assets/word-licenses/`.
