package com.roger.android_p_notification_demo

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.app.RemoteInput
import android.support.v4.app.TaskStackBuilder
import android.support.v4.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    companion object {
        val NOTIFICATION_ID = 888
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        btn_show.setOnClickListener {
            generateMessagingStyleNotification()
        }
    }


    /*
     * Generates a MESSAGING_STYLE Notification that supports both phone/tablet and wear. For
     * devices on API level 24 (7.0 - Nougat) and after, displays MESSAGING_STYLE. Otherwise,
     * displays a basic BIG_TEXT_STYLE.
     */
    private fun generateMessagingStyleNotification() {

        // Main steps for building a MESSAGING_STYLE notification:
        //      0. Get your data
        //      1. Create/Retrieve Notification Channel for O and beyond devices (26+)
        //      2. Build the MESSAGING_STYLE
        //      3. Set up main Intent for notification
        //      4. Set up RemoteInput (users can input directly from notification)
        //      5. Build and issue the notification

        // 0. Get your data (everything unique per Notification)
        val messagingStyleCommsAppData = MockDatabase.getMessagingStyleData(applicationContext)

        // 1. Create/Retrieve Notification Channel for O and beyond devices (26+).
        val notificationChannelId = NotificationUtil.createNotificationChannel(this, messagingStyleCommsAppData)

        // 2. Build the NotificationCompat.Style (MESSAGING_STYLE).
        val contentTitle = messagingStyleCommsAppData.contentTitle

        val messagingStyle = NotificationCompat.MessagingStyle(messagingStyleCommsAppData.me)
                /*
                         * <p>This API's behavior was changed in SDK version
                         * {@link Build.VERSION_CODES#P}. If your application's target version is
                         * less than {@link Build.VERSION_CODES#P}, setting a conversation title to
                         * a non-null value will make {@link #isGroupConversation()} return
                         * {@code true} and passing {@code null} will make it return {@code false}.
                         * This behavior can be overridden by calling
                         * {@link #setGroupConversation(boolean)} regardless of SDK version.
                         * In {@code P} and above, this method does not affect group conversation
                         * settings.
                         *
                         * In our case, we use the same title.
                         */
                .setConversationTitle(contentTitle)

        // Adds all Messages.
        // Note: Messages include the text, timestamp, and sender.
        for (message in messagingStyleCommsAppData.messages) {
            messagingStyle.addMessage(message)
        }

        messagingStyle.isGroupConversation = messagingStyleCommsAppData.isGroupConversation

        // 3. Set up main Intent for notification.
        val notifyIntent = Intent(this, MainActivity::class.java)

        // When creating your Intent, you need to take into account the back state, i.e., what
        // happens after your Activity launches and the user presses the back button.

        // There are two options:
        //      1. Regular activity - You're starting an Activity that's part of the application's
        //      normal workflow.

        //      2. Special activity - The user only sees this Activity if it's started from a
        //      notification. In a sense, the Activity extends the notification by providing
        //      information that would be hard to display in the notification itself.

        // Even though this sample's MainActivity doesn't link to the Activity this Notification
        // launches directly, i.e., it isn't part of the normal workflow, a chat app generally
        // always links to individual conversations as part of the app flow, so we will follow
        // option 1.

        // For an example of option 2, check out the BIG_TEXT_STYLE example.

        // For more information, check out our dev article:
        // https://developer.android.com/training/notify-user/navigation.html

        val stackBuilder = TaskStackBuilder.create(this)
        // Adds the back stack
        stackBuilder.addParentStack(MainActivity::class.java)
        // Adds the Intent to the top of the stack
        stackBuilder.addNextIntent(notifyIntent)
        // Gets a PendingIntent containing the entire back stack
        val mainPendingIntent = PendingIntent.getActivity(
                this,
                0,
                notifyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
        )


        // 4. Set up RemoteInput, so users can input (keyboard and voice) from notification.

        // Note: For API <24 (M and below) we need to use an Activity, so the lock-screen present
        // the auth challenge. For API 24+ (N and above), we use a Service (could be a
        // BroadcastReceiver), so the user can input from Notification or lock-screen (they have
        // choice to allow) without leaving the notification.

        // Create the RemoteInput specifying this key.
        val replyLabel = getString(R.string.reply_label)
        val remoteInput = RemoteInput.Builder(MessagingIntentService.EXTRA_REPLY)
                .setLabel(replyLabel)
                // Use machine learning to create responses based on previous messages.
                .setChoices(messagingStyleCommsAppData.replyChoicesBasedOnLastMessage)
                .build()

        // Pending intent =
        //      API <24 (M and below): activity so the lock-screen presents the auth challenge.
        //      API 24+ (N and above): this should be a Service or BroadcastReceiver.
        val replyActionPendingIntent: PendingIntent

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val intent = Intent(this, MessagingIntentService::class.java)
            intent.action = MessagingIntentService.ACTION_REPLY
            replyActionPendingIntent = PendingIntent.getService(this, 0, intent, 0)

        } else {
            replyActionPendingIntent = mainPendingIntent
        }

        val replyAction = NotificationCompat.Action.Builder(
                R.drawable.ic_reply_white_18dp,
                replyLabel,
                replyActionPendingIntent)
                .addRemoteInput(remoteInput)
                // Informs system we aren't bringing up our own custom UI for a reply
                // action.
                .setShowsUserInterface(false)
                // Allows system to generate replies by context of conversation.
                .setAllowGeneratedReplies(true)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                .build()


        // 5. Build and issue the notification.

        // Because we want this to be a new notification (not updating current notification), we
        // create a new Builder. Later, we update this same notification, so we need to save this
        // Builder globally (as outlined earlier).

        val notificationCompatBuilder = NotificationCompat.Builder(applicationContext, notificationChannelId?:"channelId")

        GlobalNotificationBuilder.notificationCompatBuilderInstance = notificationCompatBuilder

        notificationCompatBuilder
                // MESSAGING_STYLE sets title and content for API 16 and above devices.
                .setStyle(messagingStyle)
                // Title for API < 16 devices.
                .setContentTitle(contentTitle)
                // Content for API < 16 devices.
                .setContentText(messagingStyleCommsAppData.contentText)
                .setSmallIcon(R.drawable.ic_launcher)
                .setLargeIcon(BitmapFactory.decodeResource(
                        resources,
                        R.drawable.ic_person_black_48dp))
                .setContentIntent(mainPendingIntent)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                // Set primary color (important for Wear 2.0 Notifications).
                .setColor(ContextCompat.getColor(applicationContext, R.color.colorPrimary))

                // SIDE NOTE: Auto-bundling is enabled for 4 or more notifications on API 24+ (N+)
                // devices and all Wear devices. If you have more than one notification and
                // you prefer a different summary notification, set a group key and create a
                // summary notification via
                // .setGroupSummary(true)
                // .setGroup(GROUP_KEY_YOUR_NAME_HERE)

                // Number of new notifications for API <24 (M and below) devices.
                .setSubText(Integer.toString(messagingStyleCommsAppData.numberOfNewMessages))
                .setAutoCancel(true)
                .addAction(replyAction)
                .setCategory(Notification.CATEGORY_MESSAGE)

                // Sets priority for 25 and below. For 26 and above, 'priority' is deprecated for
                // 'importance' which is set in the NotificationChannel. The integers representing
                // 'priority' are different from 'importance', so make sure you don't mix them.
                .setPriority(messagingStyleCommsAppData.priority)

                // Sets lock-screen visibility for 25 and below. For 26 and above, lock screen
                // visibility is set in the NotificationChannel.
                .setVisibility(messagingStyleCommsAppData.channelLockscreenVisibility)

        // If the phone is in "Do not disturb" mode, the user may still be notified if the
        // sender(s) are in a group allowed through "Do not disturb" by the user.
        for (name in messagingStyleCommsAppData.participants) {
            notificationCompatBuilder.addPerson(name.uri)
        }

        val notification = notificationCompatBuilder.build()
        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
    }

}
