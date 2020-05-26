/*
 * Copyright 2020 Realm Inc.
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

#include "io_realm_internal_objectstore_OsFindIterable.h"

#include "java_class_global_def.hpp"
#include "java_network_transport.hpp"
#include "util.hpp"
#include "jni_util/java_method.hpp"
#include "jni_util/jni_utils.hpp"
#include "jni_util/bson_util.hpp"
#include "object-store/src/util/bson/bson.hpp"

#include <realm/util/optional.hpp>
#include <sync/app.hpp>
#include <sync/sync_user.hpp>
#include <sync/remote_mongo_database.hpp>
#include <sync/remote_mongo_collection.hpp>
#include <jni_util/bson_util.hpp>

using namespace realm;
using namespace realm::app;
using namespace realm::bson;
using namespace realm::jni_util;
using namespace realm::_impl;

static std::function<jobject(JNIEnv*, util::Optional<bson::BsonArray>)> collection_mapper_find = [](JNIEnv* env, util::Optional<bson::BsonArray> array) {
    return array ? JniBsonProtocol::bson_to_jstring(env, *array) : NULL;
};

JNIEXPORT void JNICALL
Java_io_realm_internal_objectstore_OsFindIterable_nativeFind(JNIEnv *env,
                                                             jclass,
                                                             jint j_find_type,
                                                             jlong j_collection_ptr,
                                                             jstring j_filter,
                                                             jstring j_projection,
                                                             jstring j_sort,
                                                             jlong j_limit,
                                                             jobject j_callback) {
    try {
        auto collection = reinterpret_cast<RemoteMongoCollection*>(j_collection_ptr);

        bson::BsonDocument filter(JniBsonProtocol::parse_checked(env, j_filter, Bson::Type::Document, "BSON filter must be a Document"));

        switch (j_find_type) {
            case io_realm_internal_objectstore_OsFindIterable_FIND:
                collection->find(filter, JavaNetworkTransport::create_result_callback(env, j_callback, collection_mapper_find));
                break;
            case io_realm_internal_objectstore_OsFindIterable_FIND_WITH_OPTIONS:
                uint64_t limit = std::uint64_t(j_limit);
                bson::BsonDocument projection(JniBsonProtocol::parse_checked(env, j_projection, Bson::Type::Document, "BSON projection must be a Document"));
                bson::BsonDocument sort(JniBsonProtocol::parse_checked(env, j_sort, Bson::Type::Document, "BSON sort must be a Document"));
                RemoteMongoCollection::RemoteFindOptions options = {
                        limit,
                        projection,
                        sort
                };
                collection->find(filter, options, JavaNetworkTransport::create_result_callback(env, j_callback, collection_mapper_find));
                break;
        }
    }
    CATCH_STD()
}