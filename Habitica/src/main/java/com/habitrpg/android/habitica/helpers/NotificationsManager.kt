package com.habitrpg.android.habitica.helpers

import android.content.Context
import com.habitrpg.android.habitica.R
import com.habitrpg.android.habitica.data.ApiClient
import com.habitrpg.android.habitica.events.ShowAchievementDialog
import com.habitrpg.android.habitica.events.ShowCheckinDialog
import com.habitrpg.android.habitica.events.ShowSnackbarEvent
import com.habitrpg.android.habitica.models.Notification
import com.habitrpg.android.habitica.models.notifications.LoginIncentiveData
import com.habitrpg.android.habitica.ui.views.HabiticaSnackbar
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.functions.Consumer
import io.reactivex.subjects.BehaviorSubject
import org.greenrobot.eventbus.EventBus
import java.util.*

class NotificationsManager (private val context: Context) {
    private val seenNotifications: MutableMap<String, Boolean>
    private var apiClient: ApiClient? = null

    private val notifications: BehaviorSubject<List<Notification>>

    private var lastNotificationHandling: Date? = null

    init {
        this.seenNotifications = HashMap()
        this.notifications = BehaviorSubject.create()
    }

    fun setNotifications(current: List<Notification>) {
        this.notifications.onNext(current)

        this.handlePopupNotifications(current)
    }

    fun getNotifications(): Flowable<List<Notification>> {
        return this.notifications
                .startWith(emptyList<Notification>())
                .toFlowable(BackpressureStrategy.LATEST)
    }

    fun getNotification(id: String): Notification? {
        return this.notifications.value?.find { it.id == id }
    }

    fun setApiClient(apiClient: ApiClient?) {
        this.apiClient = apiClient
    }

    fun handlePopupNotifications(notifications: List<Notification>): Boolean? {
        val now = Date()
        if (now.time - (lastNotificationHandling?.time ?: 0) < 500) {
            return true
        }
        lastNotificationHandling = now
        notifications
                .filter { !this.seenNotifications.containsKey(it.id) }
                .map {
                    val notificationDisplayed = when (it.type) {
                        Notification.Type.LOGIN_INCENTIVE.type -> displayLoginIncentiveNotification(it)
                        Notification.Type.ACHIEVEMENT_PARTY_UP.type -> displayAchievementNotification(it)
                        Notification.Type.ACHIEVEMENT_PARTY_ON.type -> displayAchievementNotification(it)
                        Notification.Type.ACHIEVEMENT_BEAST_MASTER.type -> displayAchievementNotification(it)
                        Notification.Type.ACHIEVEMENT_MOUNT_MASTER.type -> displayAchievementNotification(it)
                        Notification.Type.ACHIEVEMENT_TRIAD_BINGO.type -> displayAchievementNotification(it)
                        Notification.Type.ACHIEVEMENT_GUILD_JOINED.type -> displayAchievementNotification(it)
                        Notification.Type.ACHIEVEMENT_CHALLENGE_JOINED.type -> displayAchievementNotification(it)
                        Notification.Type.ACHIEVEMENT_INVITED_FRIEND.type -> displayAchievementNotification(it)
                        else -> false
                    }

                    if (notificationDisplayed == true) {
                        this.seenNotifications[it.id] = true
                    }

                }

        return true
    }

    fun displayLoginIncentiveNotification(notification: Notification): Boolean? {
        val notificationData = notification.data as? LoginIncentiveData
        val nextUnlockText = context.getString(R.string.nextPrizeUnlocks, notificationData?.nextRewardAt)
        if (notificationData?.rewardKey != null) {
            val event = ShowCheckinDialog(notification, nextUnlockText, notificationData.nextRewardAt ?: 0)
            EventBus.getDefault().post(event)
        } else {
            val event = ShowSnackbarEvent()
            event.title = notificationData?.message
            event.text = nextUnlockText
            event.type = HabiticaSnackbar.SnackbarDisplayType.BLUE
            EventBus.getDefault().post(event)
            if (apiClient != null) {
                apiClient?.readNotification(notification.id)
                        ?.subscribe(Consumer {}, RxErrorHandler.handleEmptyError())
            }
        }
        return true
    }

    private fun displayAchievementNotification(notification: Notification): Boolean {
        EventBus.getDefault().post(ShowAchievementDialog(notification.type ?: "", notification.id))
        return true
    }
}
