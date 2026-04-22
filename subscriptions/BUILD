load(
    "//java/com/google/android/gmscore/build_defs:libsculptor_mh_tests.bzl",
    "libsculptor_mh_tests",
)
load("//java/com/google/android/gmscore/build_defs:sdkaas_sample_conformance_test.bzl", "sdkaas_sample_conformance_test")
load("//third_party/java/android/android_sdk_linux/extras/android/compatibility/jetify:jetify.bzl", "jetify_android_library")
load("//tools/build_defs/android:rules.bzl", "android_binary")
load("//tools/build_defs/zip_dir:def.bzl", "zip_dir")

package(
    default_applicable_licenses = ["//third_party/googlesamples/play_billing_samples:license"],
    default_visibility = ["//third_party/googlesamples/play_billing_samples/subscriptions:__subpackages__"],
)

licenses(["notice"])

# Sample operations.
zip_dir(
    name = "sample_project_source_zip",
    srcs = [":sample_project_source_files"],
    zipname = "sample_project_source.zip",
)

Fileset(
    name = "sample_project_source_fs",
    out = "sample_project_source_files",
    entries = [
        FilesetEntry(
            files = glob(
                ["**"],
                exclude = [
                    "BUILD",
                    "METADATA",
                    "OWNERS",
                    "*.bara.sky",
                    "launch_project.sh",
                    "version_config.txt",
                    "build/**",
                    ".idea/**",
                    ".gradle/**",
                    "sampletoolsrepo/**",
                    "captures/**",
                    "local.properties",
                ],
            ),
        ),
    ],
    licenses = ["notice"],
)

jetify_android_library(
    name = "subscriptions_lib",
    srcs = glob(
        ["app/src/main/java/**/*.java"],
    ),
    custom_package = "com.google.play.billing.samples.subscriptions",
    jetify_sources = True,
    manifest = "app/src/main/AndroidManifest.xml",
    resource_files = glob(["app/src/main/res/**"]),
    deps = [
        "//java/com/google/android/libraries/play/billing/public/billing",
        "//third_party/java/androidx/appcompat",
        "//third_party/java/androidx/constraintlayout",
    ],
)

android_binary(
    name = "subscriptions_binary",
    custom_package = "com.google.play.billing.samples.subscriptions",
    manifest = "app/src/main/AndroidManifest.xml",
    resource_files = glob(["app/src/main/res/**"]),
    deps = [
        ":subscriptions_lib",
    ],
)

libsculptor_mh_tests(
    name = "sample_libsculptor",
    test_app_path = "app",
)

sdkaas_sample_conformance_test(disable_repo_tests = True)
