package com.kevinywlui.billsplit.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kevinywlui.billsplit.model.Person
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "people")
private val KEY = stringPreferencesKey("people_json")

private val json = Json { ignoreUnknownKeys = true }

class PeopleRepository(private val context: Context) {

    val people: Flow<List<Person>> = context.dataStore.data.map { prefs ->
        deserialize(prefs[KEY] ?: "[]")
    }

    suspend fun savePeople(people: List<Person>) {
        context.dataStore.edit { it[KEY] = serialize(people) }
    }

    suspend fun addPerson(name: String, venmoUsername: String): Person {
        lateinit var newPerson: Person
        context.dataStore.edit { prefs ->
            val current = deserialize(prefs[KEY] ?: "[]")
            newPerson = Person(
                name = name,
                venmoUsername = venmoUsername,
                avatarColorIndex = current.size % 10
            )
            prefs[KEY] = serialize(current + newPerson)
        }
        return newPerson
    }

    suspend fun updatePerson(person: Person) {
        context.dataStore.edit { prefs ->
            val current = deserialize(prefs[KEY] ?: "[]")
            prefs[KEY] = serialize(current.map { if (it.id == person.id) person else it })
        }
    }

    suspend fun deletePerson(personId: String) {
        context.dataStore.edit { prefs ->
            val current = deserialize(prefs[KEY] ?: "[]")
            prefs[KEY] = serialize(current.filter { it.id != personId })
        }
    }

    internal fun serialize(people: List<Person>): String = json.encodeToString(people)

    internal fun deserialize(jsonStr: String): List<Person> =
        runCatching { json.decodeFromString<List<Person>>(jsonStr) }.getOrDefault(emptyList())
}
