package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    val moshi = com.squareup.moshi.Moshi.Builder().build()
    val adapter = moshi.adapter(com.example.data.GitHubUser::class.java)
    val user = adapter.fromJson("""{"login": "testuser", "id": 123}""")
    assertEquals("testuser", user?.login)
    assertEquals(123L, user?.id)
  }
}
