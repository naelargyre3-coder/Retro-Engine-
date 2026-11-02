LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := retro_frontend
LOCAL_SRC_FILES := retro_frontend.cpp
LOCAL_C_INCLUDES := $(LOCAL_PATH)
LOCAL_CPPFLAGS := -std=c++17 -fexceptions -frtti -Wall -Wextra
LOCAL_LDLIBS := -landroid -llog -ldl
include $(BUILD_SHARED_LIBRARY)
