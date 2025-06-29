package com.ruangwarga.app

import android.content.res.Resources
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.ruangwarga.app.databinding.ActivityIntroBinding
import jp.shts.android.storiesprogressview.StoriesProgressView

class IntroActivity : AppCompatActivity() {
    private lateinit var binding: ActivityIntroBinding
    private lateinit var storiesView: StoriesProgressView
    private var pressTime = 0L
    private val limit = 500L
    private var current = 0

    private lateinit var nativeHandler: Handler
    private var nativeRunnable: Runnable? = null
    private lateinit var progressBars: List<ProgressBar>
    private val durations = longArrayOf(10000L, 10000L, 10000L, 10000L)

    private var nativeProgress = 0
    private var isNativePaused = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        initBinding()
        enableEdgeToEdge()
        initNextStoryIntro()
    }

    data class IntroItem(
        val imageRes: Int,
        val title: String,
        val description: String
    )

    private fun initBinding() {
        binding = ActivityIntroBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    private fun initNextStoryIntro() {
        val storyItems = listOf(
            IntroItem(
                R.drawable.welcome,
                getString(R.string.str_title_welcome),
                getString(R.string.str_description_welcome)
            ),
            IntroItem(
                R.drawable.schedule,
                getString(R.string.str_title_schedule),
                getString(R.string.str_description_schedule)
            ),
            IntroItem(
                R.drawable.payment,
                getString(R.string.str_title_payment),
                getString(R.string.str_description_payment)
            ),
            IntroItem(
                R.drawable.charity,
                getString(R.string.str_title_charity),
                getString(R.string.str_description_charity)
            ),
        )

        binding.nativeProgressContainer.removeAllViews()
        progressBars = durations.mapIndexed { index, _ ->
            ProgressBar(this, null, 0, android.R.attr.progressBarStyleHorizontal).apply {
                layoutParams =
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                        if (index > 0) marginStart = 4.dpToPx()
                    }
                max = 100
                progress = 0
                progressDrawable = ContextCompat.getDrawable(context, R.drawable.progress_segment)
            }
        }
        progressBars.forEach { binding.nativeProgressContainer.addView(it) }

        val imageView: ImageView = binding.imageView
        val title: TextView = binding.tvTitle
        val description: TextView = binding.tvDescription

        fun updateStory(index: Int) {
            val item = storyItems[index]
            imageView.setImageResource(item.imageRes)
            title.text = item.title
            description.text = item.description

            progressBars.forEachIndexed { i, bar ->
                bar.progress = when {
                    i < index -> 100
                    i > index -> 0
                    else -> 0
                }
            }
        }

        fun startNativeProgress(index: Int) {
            nativeRunnable?.let { nativeHandler.removeCallbacks(it) }
            nativeProgress = 0
            isNativePaused = false

            val currentBar = progressBars[index]
            currentBar.progress = 0

            nativeRunnable = object : Runnable {
                override fun run() {
                    if (isNativePaused) return
                    if (nativeProgress < 100) {
                        nativeProgress++
                        currentBar.progress = nativeProgress
                        nativeHandler.postDelayed(this, durations[index] / 100)
                    }
                }
            }
            nativeHandler.post(nativeRunnable!!)
        }

        fun pauseNativeProgress() {
            isNativePaused = true
        }

        fun resumeNativeProgress() {
            if (isNativePaused) {
                isNativePaused = false
                nativeRunnable?.let { nativeHandler.post(it) }
            }
        }

        updateStory(current)
        nativeHandler = Handler(Looper.getMainLooper())

        binding.progressContainer.alpha = 0f

        binding.progressContainer.post {
            storiesView = binding.progressContainer
            storiesView.setStoriesCountWithDurations(durations)
            storiesView.setStoriesListener(object : StoriesProgressView.StoriesListener {
                override fun onNext() {
                    current++
                    if (current < storyItems.size) {
                        updateStory(current)
                        startNativeProgress(current)
                    }
                }

                override fun onPrev() {
                    if (current > 0) {
                        current--
                        updateStory(current)
                        startNativeProgress(current)
                    }
                }

                override fun onComplete() {
                    nativeRunnable?.let { nativeHandler.removeCallbacks(it) }
                }
            })

            startNativeProgress(current)
            storiesView.startStories(current)
        }

        binding.touchSkip.setSafeTouchListener {
            if (current < storyItems.lastIndex) storiesView.skip()
        }

        binding.touchReverse.setSafeTouchListener {
            if (current > 0) storiesView.reverse()
        }

        val gestureListener: View.(() -> Unit) -> Unit = { onClick ->
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        pressTime = System.currentTimeMillis()
                        storiesView.pause()
                        pauseNativeProgress()
                        true
                    }

                    MotionEvent.ACTION_UP -> {
                        val now = System.currentTimeMillis()
                        storiesView.resume()
                        resumeNativeProgress()
                        if (now - pressTime < limit) {
                            performClick()
                            onClick()
                        }
                        true
                    }

                    MotionEvent.ACTION_CANCEL -> {
                        storiesView.resume()
                        resumeNativeProgress()
                        true
                    }

                    else -> false
                }
            }
        }

        binding.touchSkip.gestureListener { storiesView.skip() }
        binding.touchReverse.gestureListener { storiesView.reverse() }

    }

    private fun View.setSafeTouchListener(onClick: () -> Unit) {
        setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    pressTime = System.currentTimeMillis()
                    storiesView.pause()
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val now = System.currentTimeMillis()
                    storiesView.resume()
                    if (now - pressTime < limit) {
                        performClick()
                        onClick()
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    storiesView.resume()
                    true
                }

                else -> false
            }
        }
    }

    private fun Int.dpToPx(): Int = (this * Resources.getSystem().displayMetrics.density).toInt()

    override fun onDestroy() {
        storiesView.destroy()
        nativeRunnable?.let { nativeHandler.removeCallbacks(it) }
        super.onDestroy()
    }
}
