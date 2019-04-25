/*
 * Copyright 2019 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "io_realm_internal_core_IncludeDescriptor.h"

#include <realm/parser/parser.hpp>
#include <realm/parser/query_builder.hpp>
#include <object_schema.hpp>
#include <object_store.hpp>
#include <property.hpp>
#include <schema.hpp>
#include <shared_realm.hpp>

#include "java_query_descriptor.hpp"
#include "util.hpp"

using namespace realm;
using namespace realm::util;
using namespace realm::_impl;

static void finalize_descriptor(jlong ptr);
static void finalize_descriptor(jlong ptr)
{
    TR_ENTER_PTR(ptr)
    delete reinterpret_cast<IncludeDescriptor*>(ptr);
}

JNIEXPORT jlong JNICALL Java_io_realm_internal_core_IncludeDescriptor_nativeGetFinalizerMethodPtr(JNIEnv*, jclass, jlongArray columnIndexes, jlongArray tablePointers)
{
    TR_ENTER()
    try {

    return reinterpret_cast<jlong>(&finalize_descriptor);

    }
    CATCH_STD()
    return 0;
}

JNIEXPORT jlong JNICALL Java_io_realm_internal_core_IncludeDescriptor_nativeCreate(JNIEnv* env, jclass)
{
    TR_ENTER()
    try {
        return reinterpret_cast<jlong>(new IncludeDescriptor());
    }
    CATCH_STD()
    return reinterpret_cast<jlong>(nullptr);
}