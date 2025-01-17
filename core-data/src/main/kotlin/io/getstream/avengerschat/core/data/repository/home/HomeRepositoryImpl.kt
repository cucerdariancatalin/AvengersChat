/*
 * Copyright 2022 Stream.IO, Inc. All Rights Reserved.
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

package io.getstream.avengerschat.core.data.repository.home

import androidx.annotation.WorkerThread
import io.getstream.avengerschat.core.data.extensions.liveRoomInfo
import io.getstream.avengerschat.core.database.AvengersDao
import io.getstream.avengerschat.core.database.entity.mapper.asDomain
import io.getstream.avengerschat.core.model.Avenger
import io.getstream.avengerschat.core.network.AppDispatchers
import io.getstream.avengerschat.core.network.Dispatcher
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.client.models.User
import io.getstream.chat.android.client.utils.onSuccessSuspend
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import javax.inject.Inject

internal class HomeRepositoryImpl @Inject constructor(
  private val chatClient: ChatClient,
  private val avengersDao: AvengersDao,
  @Dispatcher(AppDispatchers.IO) private val dispatcher: CoroutineDispatcher
) : HomeRepository {

  init {
    Timber.d("injection HomeRepository")
  }

  /**
   * Connects a specific user using the given user token to the Stream client server.
   * This connection will keep maintained until be disconnected.
   */
  @WorkerThread
  override fun connectUser(avenger: Avenger) = flow {
    // disconnect a user if already connected.
    disconnectUser(avenger)

    val user = User(
      id = avenger.id,
      name = avenger.name,
      image = avenger.getProfileImage()
    )
    val result = chatClient.connectUser(user, avenger.token).await()
    result.onSuccessSuspend {
      emit(result.data())
    }
  }.flowOn(dispatcher)

  /**
   * Check and disconnect the current user
   * if there's already connected user to the Stream client server.
   */
  override fun disconnectUser(avenger: Avenger) {
    val currentUser = chatClient.getCurrentUser()
    if (currentUser != null && avenger.id == currentUser.id) {
      chatClient.disconnect(false).enqueue()
    }
  }

  /**
   * gets live room information list from the local database.
   */
  @WorkerThread
  override fun getLiveRoomInfo(avenger: Avenger) = flow {
    val avengers = avengersDao.getAvengers().map { it.asDomain() }
    val liveRoomInfo = avengers.filterNot { it.id == avenger.id }.map { it.liveRoomInfo }
    emit(liveRoomInfo)
  }.flowOn(dispatcher)
}
