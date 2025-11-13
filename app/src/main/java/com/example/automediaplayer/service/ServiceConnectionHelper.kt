package com.example.automediaplayer.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder

/**
 * 服务连接助手类，用于管理音乐播放服务的绑定和解绑操作
 */
class ServiceConnectionHelper {

    /** 当前已连接的服务实例，未连接或断开后为 null。 */
    private var musicService: MusicPlayService? = null

    /** 是否已通过 bindService 成功绑定，用于避免重复解绑。 */
    private var isBound = false

    /**
     * 服务连接回调接口，定义服务连接和断开连接时的回调方法
     */
    interface OnServiceConnectedListener {
        /**
         * 当服务连接成功时调用
         * @param service 连接成功的音乐播放服务实例
         */
        fun onConnected(service: MusicPlayService)

        /**
         * 当服务断开连接时调用
         */
        fun onDisconnected()
    }

    /**
     * 服务连接对象，实现ServiceConnection接口处理服务连接状态变化
     */
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            // 当系统成功建立连接时会回调此处，拿到 Binder 并缓存服务实例。
            // 获取服务绑定器并设置服务实例
            val binder = service as MusicPlayService.MusicBinder
            musicService = binder.getService()
            isBound = true
            connectedListener?.onConnected(musicService!!)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // 当服务进程被系统杀死或异常退出时触发，清理引用避免空指针。
            // 处理服务意外断开连接的情况
            musicService = null
            isBound = false
            connectedListener?.onDisconnected()
        }
    }

    /** 外部注册的回调，随着 bindService 调用而更新。 */
    private var connectedListener: OnServiceConnectedListener? = null

    /**
     * 绑定音乐播放服务。
     *
     * - bindService：建立“通信通道”，让 Activity 拿到 Service 的 binder。
     * - startService：确保 Service 真正运行在后台，即使没有组件绑定也不会被系统立即销毁。
     *   两者配合才能实现“界面退出后音乐仍可播放”。
     */
    fun bindService(context: Context, listener: OnServiceConnectedListener) {
        this.connectedListener = listener
        val intent = Intent(context, MusicPlayService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        // 同时启动服务，确保服务在后台运行
        context.startService(intent)
    }

    /**
     * 解绑音乐播放服务
     * @param context 应用上下文，用于解绑服务
     */
    fun unbindService(context: Context) {
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
            musicService = null
        }
    }
}
