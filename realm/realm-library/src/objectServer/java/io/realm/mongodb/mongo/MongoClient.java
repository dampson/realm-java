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

package io.realm.mongodb.mongo;

import org.bson.codecs.configuration.CodecRegistry;

import io.realm.RealmUser;
import io.realm.internal.Util;
import io.realm.internal.common.TaskDispatcher;
import io.realm.internal.objectstore.OsMongoClient;

/**
 * The remote MongoClient used for working with data in MongoDB remotely via Realm.
 */
public class MongoClient {

    private final OsMongoClient osMongoClient;
    private final CodecRegistry codecRegistry;
    private final TaskDispatcher dispatcher;

    /**
     * MongoClient public constructor.
     *
     * @param realmUser     user owning this client
     * @param serviceName   service name to connect to the server
     * @param codecRegistry needed for encoding and decoding database documents
     */
    public MongoClient(final RealmUser realmUser,
                       final String serviceName,
                       final CodecRegistry codecRegistry) {
        Util.checkEmpty(serviceName, "serviceName");
        this.codecRegistry = codecRegistry;
        this.dispatcher = new TaskDispatcher();
        this.osMongoClient = new OsMongoClient(realmUser, serviceName, dispatcher);
    }

    /**
     * Gets a {@link MongoDatabase} instance for the given database name.
     *
     * @param databaseName the name of the database to retrieve
     * @return a {@code RemoteMongoDatabase} representing the specified database
     */
    public MongoDatabase getDatabase(final String databaseName) {
        Util.checkEmpty(databaseName, "databaseName");
        return new MongoDatabase(osMongoClient.getDatabase(databaseName, codecRegistry), databaseName, dispatcher);
    }
}
