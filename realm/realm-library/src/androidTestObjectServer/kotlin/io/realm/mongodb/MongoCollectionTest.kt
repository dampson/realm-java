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
package io.realm.mongodb

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.realm.*
import io.realm.log.LogLevel
import io.realm.log.RealmLog
import io.realm.mongodb.mongo.MongoClient
import io.realm.mongodb.mongo.MongoCollection
import io.realm.mongodb.mongo.MongoDatabase
import io.realm.mongodb.mongo.options.CountOptions
import io.realm.mongodb.mongo.options.FindOneAndModifyOptions
import io.realm.mongodb.mongo.options.FindOptions
import io.realm.mongodb.mongo.options.UpdateOptions
import io.realm.util.blockingGetResult
import org.bson.Document
import org.bson.types.ObjectId
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.*

private const val SERVICE_NAME = "BackingDB"    // it comes from the test server's BackingDB/config.json
private const val DATABASE_NAME = "test_data"   // same as above
private const val COLLECTION_NAME = "COLLECTION_NAME"
private const val KEY_1 = "KEY"
private const val VALUE_1 = "666"

@RunWith(AndroidJUnit4::class)
class MongoCollectionTest {

    private lateinit var app: TestRealmApp
    private lateinit var user: RealmUser
    private lateinit var client: MongoClient
    private lateinit var database: MongoDatabase

    @Before
    fun setUp() {
        Realm.init(InstrumentationRegistry.getInstrumentation().targetContext)

        RealmLog.setLevel(LogLevel.DEBUG)

        app = TestRealmApp()
        user = app.registerUserAndLogin(TestHelper.getRandomEmail(), "123456")
        client = user.getMongoClient(SERVICE_NAME)
        database = client.getDatabase(DATABASE_NAME)
    }

    @After
    fun tearDown() {
        // FIXME: probably not the best way to "reset" the state
        with(getCollectionInternal(COLLECTION_NAME)) {
            deleteMany(Document()).blockingGetResult()
        }

        if (this::app.isInitialized) {
            app.close()
        }
    }

    @Test
    fun insertOne() {
        with(getCollectionInternal(COLLECTION_NAME)) {
            assertEquals(0, count().blockingGetResult())

            val doc1 = Document(mapOf("hello_1" to "1", "hello_2" to "2"))
            insertOne(doc1).blockingGetResult()
            assertEquals(1, count().blockingGetResult())

            val doc2 = Document("hello", "world")
            doc2["_id"] = ObjectId()

            assertEquals(doc2.getObjectId("_id"), insertOne(doc2).blockingGetResult()!!.insertedId.asObjectId().value)
            assertFailsWith(ObjectServerError::class) { insertOne(doc2).blockingGetResult() }

            val doc3 = Document("hello", "world")
            assertNotEquals(doc2.getObjectId("_id"), insertOne(doc3).blockingGetResult()!!.insertedId.asObjectId().value)
        }
    }

    @Test
    fun insertMany() {
        with(getCollectionInternal(COLLECTION_NAME)) {
            assertEquals(0, count().blockingGetResult())

            val rawDoc = Document(KEY_1, VALUE_1)
            val doc1 = Document(rawDoc)
            val doc2 = Document(rawDoc)
            val doc3 = Document(rawDoc)
            val doc4 = Document("foo", "bar")
            val manyDocuments = listOf(doc1, doc2, doc3, doc4)

            insertMany(manyDocuments)
                    .blockingGetResult()
                    .let { assertEquals(manyDocuments.size, it!!.insertedIds.size) }

            assertEquals(manyDocuments.size.toLong(), count().blockingGetResult())
            assertEquals(3, count(rawDoc).blockingGetResult())
            assertEquals(1, count(Document("foo", "bar")).blockingGetResult())
            assertEquals(0, count(Document("bar", "foo")).blockingGetResult())
        }
    }

    @Test
    fun count() {
        with(getCollectionInternal(COLLECTION_NAME)) {
            assertEquals(0, count().blockingGetResult())

            val rawDoc = Document("hello", "world")
            val doc1 = Document(rawDoc)
            val doc2 = Document(rawDoc)
            insertOne(doc1).blockingGetResult()
            assertEquals(1, count().blockingGetResult())
            insertOne(doc2).blockingGetResult()
            assertEquals(2, count().blockingGetResult())

            assertEquals(2, count(rawDoc).blockingGetResult())
            assertEquals(0, count(Document("hello", "Friend")).blockingGetResult())
            assertEquals(1, count(rawDoc, CountOptions().limit(1)).blockingGetResult())
            assertFails { count(Document("\$who", 1)).blockingGetResult() }
        }
    }

    @Test
    fun deleteOne_singleDocument() {
        with(getCollectionInternal(COLLECTION_NAME)) {
            assertEquals(0, count().blockingGetResult())

            val rawDoc = Document(KEY_1, VALUE_1)
            val doc1 = Document(rawDoc)

            insertOne(doc1).blockingGetResult()
            assertEquals(1, count().blockingGetResult())
            assertEquals(1, deleteOne(doc1).blockingGetResult()!!.deletedCount)
            assertEquals(0, count().blockingGetResult())
        }
    }

    @Test
    fun deleteOne_listOfDocuments() {
        with(getCollectionInternal(COLLECTION_NAME)) {
            assertEquals(0, count().blockingGetResult())

            val rawDoc = Document(KEY_1, VALUE_1)
            val doc1 = Document(rawDoc)
            val doc1b = Document(rawDoc)
            val doc2 = Document("foo", "bar")
            val doc3 = Document("42", "666")
            insertMany(listOf(doc1, doc1b, doc2, doc3)).blockingGetResult()
            assertEquals(1, deleteOne(rawDoc).blockingGetResult()!!.deletedCount)
            assertEquals(1, deleteOne(Document()).blockingGetResult()!!.deletedCount)
        }
    }

    @Test
    fun deleteMany_singleDocument() {
        with(getCollectionInternal(COLLECTION_NAME)) {
            assertEquals(0, count().blockingGetResult())

            val rawDoc = Document(KEY_1, VALUE_1)
            val doc1 = Document(rawDoc)

            insertOne(doc1).blockingGetResult()
            assertEquals(1, count().blockingGetResult())
            assertEquals(1, deleteMany(doc1).blockingGetResult()!!.deletedCount)
            assertEquals(0, count().blockingGetResult())
        }
    }

    @Test
    fun deleteMany_listOfDocuments() {
        with(getCollectionInternal(COLLECTION_NAME)) {
            assertEquals(0, count().blockingGetResult())

            val rawDoc = Document(KEY_1, VALUE_1)
            val doc1 = Document(rawDoc)
            val doc1b = Document(rawDoc)
            val doc2 = Document("foo", "bar")
            val doc3 = Document("42", "666")
            insertMany(listOf(doc1, doc1b, doc2, doc3)).blockingGetResult()
            assertEquals(2, deleteMany(rawDoc).blockingGetResult()!!.deletedCount)                 // two docs will be deleted
            assertEquals(2, count().blockingGetResult())                                           // two docs still present
            assertEquals(2, deleteMany(Document()).blockingGetResult()!!.deletedCount)             // delete all
            assertEquals(0, count().blockingGetResult())

            insertMany(listOf(doc1, doc1b, doc2, doc3)).blockingGetResult()
            assertEquals(4, deleteMany(Document()).blockingGetResult()!!.deletedCount)             // delete all
            assertEquals(0, count().blockingGetResult())
        }
    }

    @Test
    fun findOne() {
        with(getCollectionInternal(COLLECTION_NAME)) {
            val doc1 = Document("hello", "world1")
            val doc2 = Document("hello", "world2")
            val doc3 = Document("hello", "world3")

            // Test findOne() on empty collection with no filter and no options
            assertNull(findOne().blockingGetResult())

            // Insert a document into the collection
            insertOne(doc1).blockingGetResult()
            assertEquals(1, count().blockingGetResult())

            // Test findOne() with no filter and no options
            assertEquals(doc1, findOne().blockingGetResult()!!.withoutId())

            // Test findOne() with filter that does not match any documents and no options
            assertNull(findOne(Document("hello", "worldDNE")).blockingGetResult())

            // Insert 2 more documents into the collection
            insertMany(listOf(doc2, doc3)).blockingGetResult()
            assertEquals(3, count().blockingGetResult())

            // test findOne() with projection and sort options
            val projection = Document("hello", 1)
            projection["_id"] = 0
            val options1 = FindOptions()
                    .limit(2)
                    .projection(projection)
                    .sort(Document("hello", 1))
            assertEquals(doc1, findOne(Document(), options1).blockingGetResult()!!.withoutId())

            val options2 = FindOptions()
                    .limit(2)
                    .projection(projection)
                    .sort(Document("hello", -1))
            assertEquals(doc3.withoutId(), findOne(Document(), options2).blockingGetResult()!!.withoutId())

            assertFails { findOne(Document("\$who", 1)).blockingGetResult() }
        }
    }

    @Test
    fun updateOne() {
        with(getCollectionInternal(COLLECTION_NAME)) {
            val doc1 = Document("hello", "world")
            val result1 = updateOne(Document(), doc1).blockingGetResult()!!
            assertEquals(0, result1.matchedCount)
            assertEquals(0, result1.modifiedCount)
            assertNull(result1.upsertedId)

            val options2 = UpdateOptions().upsert(true)
            val result2 = updateOne(Document(), doc1, options2).blockingGetResult()!!
            assertEquals(0, result2.matchedCount)
            assertEquals(0, result2.modifiedCount)
            assertFalse(result2.upsertedId!!.isNull)

            val result3 = updateOne(Document(), Document("\$set", Document("woof", "meow"))).blockingGetResult()!!
            assertEquals(1, result3.matchedCount)
            assertEquals(1, result3.modifiedCount)
            assertNull(result3.upsertedId)

            // FIXME: revisit when parser is fully operational
            val expectedDoc = Document("hello", "world")
            expectedDoc["woof"] = "meow"
            assertEquals(expectedDoc, find(Document()).first().blockingGetResult()!!.withoutId())

            assertFails { updateOne(Document("\$who", 1), Document()).blockingGetResult() }
        }
    }

    @Test
    fun updateMany() {
        with(getCollectionInternal(COLLECTION_NAME)) {
            val doc1 = Document("hello", "world")
            val result1 = updateMany(Document(), doc1).blockingGetResult()!!
            assertEquals(0, result1.matchedCount)
            assertEquals(0, result1.modifiedCount)
            assertNull(result1.upsertedId)

            val options2 = UpdateOptions().upsert(true)
            val result2 = updateMany(Document(), doc1, options2).blockingGetResult()!!
            assertEquals(0, result2.matchedCount)
            assertEquals(0, result2.modifiedCount)
            assertNotNull(result2.upsertedId)

            val result3 = updateMany(Document(), Document("\$set", Document("woof", "meow"))).blockingGetResult()!!
            assertEquals(1, result3.matchedCount)
            assertEquals(1, result3.modifiedCount)
            assertNull(result3.upsertedId)

            insertOne(Document()).blockingGetResult()
            val result4 = updateMany(Document(), Document("\$set", Document("woof", "meow"))).blockingGetResult()!!
            assertEquals(2, result4.matchedCount)
            assertEquals(2, result4.modifiedCount)
        }
    }

    @Test
    fun findOneAndUpdate() {
        with(getCollectionInternal(COLLECTION_NAME)) {
            val sampleDoc = Document("hello", "world1")
            sampleDoc["num"] = 2

            // Collection should start out empty
            // This also tests the null return format
            assertNull(findOneAndUpdate(Document(), Document()).blockingGetResult())

            // Insert a sample Document
            insertOne(sampleDoc).blockingGetResult()
            assertEquals(1, count().blockingGetResult())

            // Sample call to findOneAndUpdate() where we get the previous document back
            val sampleUpdate = Document("\$set", Document("hello", "hellothere"))
            sampleUpdate["\$inc"] = Document("num", 1)
            assertEquals(
                    sampleDoc.withoutId(),
                    findOneAndUpdate(
                            Document("hello", "world1"),
                            sampleUpdate
                    ).blockingGetResult()!!.withoutId()
            )
            assertEquals(1, count().blockingGetResult())

            // Make sure the update took place
            val expectedDoc = Document("hello", "hellothere")
            expectedDoc["num"] = 3
            assertEquals(
                    expectedDoc.withoutId(),
                    find().first().blockingGetResult()!!.withoutId()
            )
            assertEquals(1, count().blockingGetResult())

            // Call findOneAndUpdate() again but get the new document
            sampleUpdate.remove("\$set")
            expectedDoc["num"] = 4
            val result = findOneAndUpdate(
                    Document("hello", "hellothere"),
                    sampleUpdate,
                    FindOneAndModifyOptions()
                            .returnNewDocument(true)
            ).blockingGetResult()
            assertEquals(expectedDoc.withoutId(), result!!.withoutId())
            assertEquals(1, count().blockingGetResult())

            // Test null behaviour again with a filter that should not match any documents
            /**
             *
             *

            FIXME: bug, we are searching for something that isn't there, so it should return null, but it does in fact return something

             Collection contains:
            {"_id": {"$oid": "5eccc1eed93d045d73907621"}, "hello": "hellothere", "num": 4}

             Request being sent:
            {"arguments":[{"database":"test_data","collection":"COLLECTION_NAME","query":{"hello":"zzzzz"},"update":{}}],"name":"findOneAndUpdate","service":"BackingDB"}

             Response - should be null:
            {"_id": {"$oid": "5eccc1eed93d045d73907621"}, "hello": "hellothere", "num": 4}

             *
             *
             */
            val nullResponse = findOneAndUpdate(
                    Document("hello", "zzzzz"),
                    Document()
            ).blockingGetResult()
            assertNull(nullResponse)
            assertEquals(1, count().blockingGetResult())

            val doc1 = Document("hello", "world1")
            doc1["num"] = 1

            val doc2 = Document("hello", "world2")
            doc2["num"] = 2

            val doc3 = Document("hello", "world3")
            doc3["num"] = 3

            // Test the upsert option where it should not actually be invoked
            val options2 = FindOneAndModifyOptions()
                    .returnNewDocument(true)
                    .upsert(true)
            val result2 = findOneAndUpdate(
                    Document("hello", "hellothere"),
                    Document("\$set", doc1),
                    options2
            ).blockingGetResult()
            assertEquals(doc1, result2!!.withoutId())
            assertEquals(1, count().blockingGetResult())
            assertEquals(
                    doc1.withoutId(),
                    find().first().blockingGetResult()!!.withoutId()
            )

            // Test the upsert option where the server should perform upsert and return new document
            val options3 = FindOneAndModifyOptions()
                    .returnNewDocument(true)
                    .upsert(true)
            /**
             *
             *

            FIXME: bug, should not find anything so value should be upserted, however it updates it with the "update" value

            Collection contains:
            {"_id": {"$oid": "5eccc404d93d045d739076c5"}, "hello": "world1", "num": 1}

            Request being sent:
            {"arguments":[{"database":"test_data","collection":"COLLECTION_NAME","query":{"hello":"hellothere"},"update":{"$set":{"hello":"world2","num":{"$numberInt":"2"}}},"upsert":true,"returnNewDocument":true,"project":{},"sort":{}}],"name":"findOneAndUpdate","service":"BackingDB"}

             *
             *
             */
            val result3 = findOneAndUpdate(
                    Document("hello", "hellothere"),
                    Document("\$set", doc2),
                    options3
            ).blockingGetResult()
            assertEquals(doc2, result3!!.withoutId())
            assertEquals(1, count().blockingGetResult())        // FIXME: the original value was 2 in Stitch - investigate

            // Test the upsert option where the server should perform upsert and return old document
            // The old document should be empty
            val options4 = FindOneAndModifyOptions()
                    .upsert(true)
            val result4= findOneAndUpdate(
                    Document("hello", "hellothere"),
                    Document("\$set", doc3),
                    options4
            ).blockingGetResult()
            assertNull(result4)
            assertEquals(3, count().blockingGetResult())

            // Test sort and project
            val sampleProject = Document("hello", 1)
            sampleProject["_id"] = 0

            val options5 = FindOneAndModifyOptions()
                    .projection(sampleProject)
                    .sort(Document("num", 1))
            val result5 = findOneAndUpdate(
                    Document(),
                    sampleUpdate,
                    options5
            ).blockingGetResult()
            assertEquals(Document("hello", "world1"), result5!!.withoutId())
            assertEquals(3, count().blockingGetResult())

            val options6 = FindOneAndModifyOptions()
                    .projection(sampleProject)
                    .sort(Document("num", -1))
            val result6 = findOneAndUpdate(
                    Document(),
                    sampleUpdate,
                    options6
            ).blockingGetResult()
            assertEquals(Document("hello", "world3"), result6!!.withoutId())
            assertEquals(3, count().blockingGetResult())

            // Test proper failure
            assertFails { findOneAndUpdate(Document(), Document("\$who", 1)) }
            assertFails { findOneAndUpdate(Document(), Document("\$who", 1), FindOneAndModifyOptions().upsert(true)) }
        }
    }

    @Test
    fun find() {
        with(getCollectionInternal(COLLECTION_NAME)) {
            // FIXME: fix find implementation - ignore this code for code review
            val find = find()
            val iter = find.iterator()
            assertFalse(iter.blockingGetResult()!!.hasNext().blockingGetResult()!!)
            assertNull(find.first().blockingGetResult())

            val doc1 = Document("hello", "world")
            val doc2 = Document("hello", "friend")
            doc2["proj"] = "field"
            insertMany(listOf(doc1, doc2)).blockingGetResult()
            val hasNext = iter.blockingGetResult()!!.hasNext()
            val actual = hasNext.blockingGetResult()!!
            assertTrue(actual)
        }
    }

    // FIXME: more to come

    private fun getCollectionInternal(collectionName: String, javaClass: Class<Document>? = null): MongoCollection<Document> {
        return when (javaClass) {
            null -> database.getCollection(collectionName)
            else -> database.getCollection(collectionName, javaClass)
        }
    }

    private fun Document.withId(objectId: ObjectId? = null): Document {
        return apply { this["_id"] = objectId ?: ObjectId() }
    }

    private fun Document.withoutId(): Document {
        return apply { remove("_id") }
    }
}
