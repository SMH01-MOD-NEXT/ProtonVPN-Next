/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

#include <jni.h>
#include <string>
#include <vector>
#include <set>
#include <sys/ptrace.h>
#include <android/log.h>
#include <android/native_window_jni.h>
#include <android/asset_manager_jni.h>
#include "connection.h"
#include "antitamper.h"
#include "utils.h"
#include "obfuscation.h"
#include "auth.h"
#include "sentry_manager.h"
#include "security_metadata.h"

using namespace next;

// Global JNI cache definitions (shared across translation units via api.h)
namespace next {
    jclass g_vpn_manager_class = nullptr;
    jmethodID g_perform_request_mid = nullptr;
    jclass g_native_response_class = nullptr;
}

static jstring generateConfig(
    JNIEnv* env, jobject /* thiz */, jstring server_public_key, jstring private_key, jstring local_ip, jstring dns_server, jstring target_ip, jboolean is_include_mode, jobjectArray selected_apps, jobjectArray selected_ips, jint port, jstring certificate, jobject obfuscation_params
) {
    auto jstringToString = [&](jstring jstr) -> std::string {
        if (!jstr) return "";
        const char* chars = env->GetStringUTFChars(jstr, nullptr);
        std::string str(chars);
        env->ReleaseStringUTFChars(jstr, chars);
        return str;
    };
    auto jobjectArrayToSet = [&](jobjectArray array) -> std::set<std::string> {
        std::set<std::string> result;
        if (!array) return result;
        jsize length = env->GetArrayLength(array);
        for (jsize i = 0; i < length; ++i) {
            jstring jstr = (jstring)env->GetObjectArrayElement(array, i);
            result.insert(jstringToString(jstr));
            env->DeleteLocalRef(jstr);
        }
        return result;
    };
    next::ObfuscationParams params;
    jclass params_class = env->GetObjectClass(obfuscation_params);
    auto getIntField = [&](const char* field_name) -> int {
        jfieldID field_id = env->GetFieldID(params_class, field_name, "I");
        return env->GetIntField(obfuscation_params, field_id);
    };
    auto getStringField = [&](const char* field_name) -> std::string {
        jfieldID field_id = env->GetFieldID(params_class, field_name, "Ljava/lang/String;");
        return jstringToString((jstring)env->GetObjectField(obfuscation_params, field_id));
    };
    params.jc = getIntField("jc"); params.jmin = getIntField("jmin"); params.jmax = getIntField("jmax"); params.s1 = getIntField("s1"); params.s2 = getIntField("s2"); params.s3 = getIntField("s3"); params.s4 = getIntField("s4");
    params.h1 = getStringField("h1"); params.h2 = getStringField("h2"); params.h3 = getStringField("h3"); params.h4 = getStringField("h4");
    params.i1 = getStringField("i1"); params.i2 = getStringField("i2"); params.i3 = getStringField("i3"); params.i4 = getStringField("i4"); params.i5 = getStringField("i5");

    std::string config = next::ConfigGenerator::buildConfig(jstringToString(server_public_key), jstringToString(private_key), jstringToString(local_ip), jstringToString(dns_server), jstringToString(target_ip), (bool)is_include_mode, jobjectArrayToSet(selected_apps), jobjectArrayToSet(selected_ips), (int)port, jstringToString(certificate), params);
    return env->NewStringUTF(config.c_str());
}

static jboolean isValidIpOrCidr(JNIEnv* env, jobject /* thiz */, jstring input) {
    const char* chars = env->GetStringUTFChars(input, nullptr);
    bool result = next::IpSubnetCalculator::isValidIpOrCidr(chars);
    env->ReleaseStringUTFChars(input, chars);
    return result;
}

static jobjectArray complementOfExcluded(JNIEnv* env, jobject /* thiz */, jobjectArray excluded_cidrs) {
    std::vector<std::string> excluded;
    jsize length = env->GetArrayLength(excluded_cidrs);
    for (jsize i = 0; i < length; ++i) {
        jstring jstr = (jstring)env->GetObjectArrayElement(excluded_cidrs, i);
        const char* chars = env->GetStringUTFChars(jstr, nullptr);
        excluded.push_back(chars);
        env->ReleaseStringUTFChars(jstr, chars);
        env->DeleteLocalRef(jstr);
    }
    std::vector<std::string> result = next::IpSubnetCalculator::complementOfExcluded(excluded);
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray resultArray = env->NewObjectArray(result.size(), stringClass, nullptr);
    for (size_t i = 0; i < result.size(); ++i) env->SetObjectArrayElement(resultArray, i, env->NewStringUTF(result[i].c_str()));
    return resultArray;
}

static void setState(JNIEnv* /* env */, jobject /* thiz */, jint state) {
    g_vpn_manager.setState(static_cast<next::VpnState>(state));
}

static jint getState(JNIEnv* /* env */, jobject /* thiz */) {
    return static_cast<jint>(g_vpn_manager.getState());
}

static jboolean canConnect(JNIEnv* /* env */, jobject /* thiz */) {
    return g_vpn_manager.canConnect();
}

static jboolean canDisconnect(JNIEnv* /* env */, jobject /* thiz */) {
    return g_vpn_manager.canDisconnect();
}

static jstring getProtectedString(JNIEnv* env, jobject /* thiz */, jstring locale, jstring key) {
    const char* localeChars = env->GetStringUTFChars(locale, nullptr);
    const char* keyChars = env->GetStringUTFChars(key, nullptr);
    std::string result = next::AntiTamper::getProtectedString(localeChars, keyChars);
    env->ReleaseStringUTFChars(locale, localeChars);
    env->ReleaseStringUTFChars(key, keyChars);
    return env->NewStringUTF(result.c_str());
}

static jboolean isTamperDetected(JNIEnv* /* env */, jobject /* thiz */) {
    return g_vpn_manager.isTamperDetected();
}

extern "C" JNIEXPORT jobject JNICALL
Java_ru_protonmod_next_vpn_AntiTamperBridge_invokeNative(JNIEnv* env, jobject /* thiz */, jlong handlerAddr, jobject proxy, jstring methodName, jobjectArray args) {
    const char* methodChars = env->GetStringUTFChars(methodName, nullptr);
    std::string method(methodChars);
    env->ReleaseStringUTFChars(methodName, methodChars);

    if (handlerAddr == 1) { // SurfaceHolder.Callback
        if (method == XOR_STR("surfaceCreated")) {
            jobject holder = env->GetObjectArrayElement(args, 0);
            jclass holderClass = env->GetObjectClass(holder);
            jmethodID getSurfaceMethod = env->GetMethodID(holderClass, XOR_STR("getSurface").c_str(), XOR_STR("()Landroid/view/Surface;").c_str());
            jobject surface = env->CallObjectMethod(holder, getSurfaceMethod);
            ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
            next::AntiTamper::initImGui(window);
        } else if (method == XOR_STR("surfaceDestroyed")) {
            next::AntiTamper::initImGui(nullptr);
        }
    } else if (handlerAddr == 2) { // OnTouchListener
        if (method == XOR_STR("onTouch")) {
            jobject event = env->GetObjectArrayElement(args, 1);
            jclass eventClass = env->GetObjectClass(event);
            jmethodID getXMethod = env->GetMethodID(eventClass, XOR_STR("getX").c_str(), XOR_STR("()F").c_str());
            jmethodID getYMethod = env->GetMethodID(eventClass, XOR_STR("getY").c_str(), XOR_STR("()F").c_str());
            jmethodID getActionMethod = env->GetMethodID(eventClass, XOR_STR("getAction").c_str(), XOR_STR("()I").c_str());

            float x = env->CallFloatMethod(event, getXMethod);
            float y = env->CallFloatMethod(event, getYMethod);
            int action = env->CallIntMethod(event, getActionMethod);

            next::AntiTamper::handleInputEvent(x, y, action);

            // React to ImGui flags on the UI thread
            if (next::AntiTamper::g_download_clicked) {
                next::AntiTamper::g_download_clicked = false;
                jobject activity = next::AntiTamper::getCurrentActivity(env);
                if (activity) {
                    next::AntiTamper::handleDownloadOfficial(env, activity);
                    env->DeleteLocalRef(activity);
                }
            }
            if (next::AntiTamper::g_accept_clicked) {
                next::AntiTamper::g_accept_clicked = false;
                jobject activity = next::AntiTamper::getCurrentActivity(env);
                if (activity) {
                    next::AntiTamper::handleAcceptRisks(env, activity, "", "");
                    next::AntiTamper::dismissNativeOverlay(env);
                    env->DeleteLocalRef(activity);
                }
            }

            // Return true to indicate handled
            jclass booleanClass = env->FindClass("java/lang/Boolean");
            jmethodID booleanInit = env->GetMethodID(booleanClass, "<init>", "(Z)V");
            return env->NewObject(booleanClass, booleanInit, JNI_TRUE);
        }
    } else if (handlerAddr == 3) { // ActivityLifecycleCallbacks
        if (method == XOR_STR("onActivityResumed")) {
            jobject activity = env->GetObjectArrayElement(args, 0);
            next::AntiTamper::onActivityResumed(env, activity);
        }
    }

    if (method == "toString") return env->NewStringUTF("AntiTamperProxy");
    return nullptr;
}

static void setLogcatEnabled(JNIEnv* /* env */, jobject /* thiz */, jboolean enabled) {
    next::AntiTamper::setLogcatEnabled(enabled);
}

static jstring getSentryDsn(JNIEnv* env, jobject /* thiz */) {
    return env->NewStringUTF(next::SentryManager::getSentryDsn().c_str());
}

static jobject loginNative(JNIEnv* env, jobject /* thiz */, jstring username, jstring password, jstring captchaToken) {
    const char* userChars = env->GetStringUTFChars(username, nullptr);
    const char* passChars = env->GetStringUTFChars(password, nullptr);
    const char* captchaChars = captchaToken ? env->GetStringUTFChars(captchaToken, nullptr) : "";

    LoginResult res = AuthManager::login(env, userChars, passChars, captchaChars ? captchaChars : "");

    env->ReleaseStringUTFChars(username, userChars);
    env->ReleaseStringUTFChars(password, passChars);
    if (captchaToken) env->ReleaseStringUTFChars(captchaToken, captchaChars);

    jclass resultClass = env->FindClass(XOR_STR("ru/protonmod/next/data/network/NativeLoginResult").c_str());
    jmethodID resultInit = env->GetMethodID(resultClass, "<init>", "()V");
    jobject jResult = env->NewObject(resultClass, resultInit);

    auto setStringField = [&](const char* name, const std::string& value) {
        jfieldID fieldId = env->GetFieldID(resultClass, name, "Ljava/lang/String;");
        jstring jVal = env->NewStringUTF(value.c_str());
        env->SetObjectField(jResult, fieldId, jVal);
        env->DeleteLocalRef(jVal);
    };

    auto setBoolField = [&](const char* name, bool value) {
        jfieldID fieldId = env->GetFieldID(resultClass, name, "Z");
        env->SetBooleanField(jResult, fieldId, value);
    };

    auto setIntField = [&](const char* name, int value) {
        jfieldID fieldId = env->GetFieldID(resultClass, name, "I");
        env->SetIntField(jResult, fieldId, value);
    };

    setBoolField("success", res.success);
    setIntField("code", res.code);
    setStringField("accessToken", res.accessToken);
    setStringField("refreshToken", res.refreshToken);
    setStringField("sessionId", res.sessionId);
    setStringField("userId", res.userId);
    setStringField("error", res.error);
    setBoolField("captchaRequired", res.captchaRequired);
    setStringField("captchaUrl", res.captchaUrl);
    setStringField("captchaToken", res.captchaToken);

    // Set scopes as string array
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray scopesArray = env->NewObjectArray(res.scopes.size(), stringClass, nullptr);
    for (size_t i = 0; i < res.scopes.size(); ++i) {
        env->SetObjectArrayElement(scopesArray, i, env->NewStringUTF(res.scopes[i].c_str()));
    }
    jfieldID scopesField = env->GetFieldID(resultClass, "scopes", "[Ljava/lang/String;");
    env->SetObjectField(jResult, scopesField, scopesArray);

    return jResult;
}

extern "C" jint JNI_OnLoad(JavaVM* vm, void* /* reserved */) {
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;

    // Claim our own ptrace slot so no external process (GameGuardian, GameKiller, etc.)
    // can attach and read/write our process memory via ptrace(PTRACE_ATTACH).
    ptrace(PTRACE_TRACEME, 0, nullptr, nullptr);

    // Register NextConfigGenerator methods
    {
        jclass generatorClass = env->FindClass(XOR_STR("ru/protonmod/next/vpn/NextConfigGenerator").c_str());
        if (generatorClass) {
            std::string m1_name = XOR_STR("generateConfigNative");
            std::string m1_sig = XOR_STR("(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Z[Ljava/lang/String;[Ljava/lang/String;ILjava/lang/String;Lru/protonmod/next/vpn/AmneziaVpnManager$ObfuscationParams;)Ljava/lang/String;");
            JNINativeMethod m[] = {{(char*)m1_name.c_str(), (char*)m1_sig.c_str(), (void*)generateConfig}};
            env->RegisterNatives(generatorClass, m, 1);
        }
    }

    // Register NextIpSubnetCalculator methods
    {
        jclass calculatorClass = env->FindClass(XOR_STR("ru/protonmod/next/vpn/IpSubnetCalculatorImpl").c_str());
        if (calculatorClass) {
            std::string m1_name = XOR_STR("isValidIpOrCidrNative");
            std::string m1_sig = XOR_STR("(Ljava/lang/String;)Z");
            std::string m2_name = XOR_STR("complementOfExcludedNative");
            std::string m2_sig = XOR_STR("([Ljava/lang/String;)[Ljava/lang/String;");
            JNINativeMethod m[] = {
                {(char*)m1_name.c_str(), (char*)m1_sig.c_str(), (void*)isValidIpOrCidr},
                {(char*)m2_name.c_str(), (char*)m2_sig.c_str(), (void*)complementOfExcluded}
            };
            env->RegisterNatives(calculatorClass, m, 2);
        }
    }

    // Register NextVpnManager methods
    {
        jclass managerClass = env->FindClass(XOR_STR("ru/protonmod/next/vpn/NextVpnManager").c_str());
        if (managerClass) {
            next::g_vpn_manager_class = (jclass)env->NewGlobalRef(managerClass);
            next::g_perform_request_mid = env->GetStaticMethodID(next::g_vpn_manager_class, XOR_STR("performNativeRequest").c_str(),
                XOR_STR("(Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;Ljava/lang/String;)Lru/protonmod/next/vpn/NextVpnManager$NativeResponse;").c_str());

            std::string n_setState = XOR_STR("setStateNative");
            std::string s_setState = XOR_STR("(I)V");
            std::string n_getState = XOR_STR("getStateNative");
            std::string s_getState = XOR_STR("()I");
            std::string n_canConnect = XOR_STR("canConnectNative");
            std::string s_canConnect = XOR_STR("()Z");
            std::string n_canDisconnect = XOR_STR("canDisconnectNative");
            std::string s_canDisconnect = XOR_STR("()Z");
            std::string n_isTamper = XOR_STR("isTamperDetectedNative");
            std::string s_isTamper = XOR_STR("()Z");
            std::string n_getProt = XOR_STR("getProtectedStringNative");
            std::string s_getProt = XOR_STR("(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;");
            std::string n_setLog = XOR_STR("setLogcatEnabledNative");
            std::string s_setLog = XOR_STR("(Z)V");

            JNINativeMethod m[] = {
                {(char*)n_setState.c_str(), (char*)s_setState.c_str(), (void*)setState},
                {(char*)n_getState.c_str(), (char*)s_getState.c_str(), (void*)getState},
                {(char*)n_canConnect.c_str(), (char*)s_canConnect.c_str(), (void*)canConnect},
                {(char*)n_canDisconnect.c_str(), (char*)s_canDisconnect.c_str(), (void*)canDisconnect},
                {(char*)n_isTamper.c_str(), (char*)s_isTamper.c_str(), (void*)isTamperDetected},
                {(char*)n_getProt.c_str(), (char*)s_getProt.c_str(), (void*)getProtectedString},
                {(char*)n_setLog.c_str(), (char*)s_setLog.c_str(), (void*)setLogcatEnabled}
            };
            env->RegisterNatives(managerClass, m, 7);
        }

        jclass responseClass = env->FindClass(XOR_STR("ru/protonmod/next/vpn/NextVpnManager$NativeResponse").c_str());
        if (responseClass) {
            next::g_native_response_class = (jclass)env->NewGlobalRef(responseClass);
        }
    }

    // Register AuthRepository native methods
    {
        jclass authClass = env->FindClass(XOR_STR("ru/protonmod/next/data/network/AuthNativeBridgeImpl").c_str());
        if (authClass) {
            std::string n_login = XOR_STR("loginNative");
            std::string s_login = XOR_STR("(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Lru/protonmod/next/data/network/NativeLoginResult;");
            JNINativeMethod m[] = {{(char*)n_login.c_str(), (char*)s_login.c_str(), (void*)loginNative}};
            env->RegisterNatives(authClass, m, 1);
        }
    }

    // Register AntiTamperBridge methods
    {
        jclass bridgeClass = env->FindClass(XOR_STR("ru/protonmod/next/vpn/AntiTamperBridge").c_str());
        if (bridgeClass) {
            std::string n_invoke = XOR_STR("invokeNative");
            std::string s_invoke = XOR_STR("(JLjava/lang/Object;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;");
            JNINativeMethod m[] = {{(char*)n_invoke.c_str(), (char*)s_invoke.c_str(), (void*)Java_ru_protonmod_next_vpn_AntiTamperBridge_invokeNative}};
            env->RegisterNatives(bridgeClass, m, 1);
        }
    }

    // Register SentryBridge native methods
    {
        jclass sentryClass = env->FindClass(XOR_STR("ru/protonmod/next/vpn/SentryBridge").c_str());
        if (sentryClass) {
            std::string n_getDsn = XOR_STR("getSentryDsnNative");
            std::string s_getDsn = XOR_STR("()Ljava/lang/String;");
            JNINativeMethod m[] = {{(char*)n_getDsn.c_str(), (char*)s_getDsn.c_str(), (void*)getSentryDsn}};
            env->RegisterNatives(sentryClass, m, 1);
        }
    }


    // Get Application Context via ActivityThread to perform automatic check
    jclass activityThreadClass = env->FindClass(XOR_STR("android/app/ActivityThread").c_str());
    if (activityThreadClass) {
        jmethodID currentApplicationMethod = env->GetStaticMethodID(activityThreadClass, XOR_STR("currentApplication").c_str(), XOR_STR("()Landroid/app/Application;").c_str());
        if (currentApplicationMethod) {
            jobject context = env->CallStaticObjectMethod(activityThreadClass, currentApplicationMethod);
            if (context) {
                // Initialize AssetManager for font loading
                jclass contextClass = env->GetObjectClass(context);
                jmethodID getAssetsMethod = env->GetMethodID(contextClass, XOR_STR("getAssets").c_str(), XOR_STR("()Landroid/content/res/AssetManager;").c_str());
                jobject assets = env->CallObjectMethod(context, getAssetsMethod);
                if (assets) {
                    AntiTamper::setAssetManager(AAssetManager_fromJava(env, assets));
                }

                // Initialize Sentry Native independently
                jmethodID getCacheDirMethod = env->GetMethodID(contextClass, XOR_STR("getCacheDir").c_str(), XOR_STR("()Ljava/io/File;").c_str());
                jobject cacheFile = env->CallObjectMethod(context, getCacheDirMethod);
                jclass fileClass = env->FindClass(XOR_STR("java/io/File").c_str());
                jmethodID getPathMethod = env->GetMethodID(fileClass, XOR_STR("getAbsolutePath").c_str(), XOR_STR("()Ljava/lang/String;").c_str());
                jstring cachePath = (jstring)env->CallObjectMethod(cacheFile, getPathMethod);
                const char* cachePathChars = env->GetStringUTFChars(cachePath, nullptr);

                // Fetch version info from PackageInfo
                jmethodID getPackageNameMethod = env->GetMethodID(contextClass, XOR_STR("getPackageName").c_str(), XOR_STR("()Ljava/lang/String;").c_str());
                jstring packageName = (jstring)env->CallObjectMethod(context, getPackageNameMethod);

                jmethodID getPackageManagerMethod = env->GetMethodID(contextClass, XOR_STR("getPackageManager").c_str(), XOR_STR("()Landroid/content/pm/PackageManager;").c_str());
                jobject packageManager = env->CallObjectMethod(context, getPackageManagerMethod);
                jclass packageManagerClass = env->GetObjectClass(packageManager);
                jmethodID getPackageInfoMethod = env->GetMethodID(packageManagerClass, XOR_STR("getPackageInfo").c_str(), XOR_STR("(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;").c_str());

                jobject packageInfo = env->CallObjectMethod(packageManager, getPackageInfoMethod, packageName, 0);
                jclass packageInfoClass = env->GetObjectClass(packageInfo);

                jfieldID versionNameField = env->GetFieldID(packageInfoClass, XOR_STR("versionName").c_str(), XOR_STR("Ljava/lang/String;").c_str());
                jstring versionName = (jstring)env->GetObjectField(packageInfo, versionNameField);
                const char* versionNameChars = env->GetStringUTFChars(versionName, nullptr);

                jfieldID versionCodeField = env->GetFieldID(packageInfoClass, XOR_STR("versionCode").c_str(), XOR_STR("I").c_str());
                int versionCode = env->GetIntField(packageInfo, versionCodeField);

                const char* packagePathChars = env->GetStringUTFChars(packageName, nullptr);

                // Sentry Native initialization removed (now a no-op)
                SentryManager::init(cachePathChars, false, versionNameChars, versionCode, packagePathChars);

                env->ReleaseStringUTFChars(packageName, packagePathChars);
                env->ReleaseStringUTFChars(versionName, versionNameChars);
                env->ReleaseStringUTFChars(cachePath, cachePathChars);

                // Automatically register lifecycle callbacks in native code
                AntiTamper::registerLifecycleCallbacks(env, context);

                env->DeleteLocalRef(context);
            }
        }
    }
    return JNI_VERSION_1_6;
}
