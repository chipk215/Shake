package com.keyeswest.shake

import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Environment
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Window
import android.view.WindowManager
import android.widget.Switch
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.LegendRenderer
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import java.io.File
import java.io.FileOutputStream


class MainActivity : AppCompatActivity(), SensorEventListener {

    private val TAG = "MAIN_ACTIVITY"

    private var sensorManager: SensorManager? = null

    private var graph: GraphView? = null
    private var sampleCount: Double = 0.0

    private var switch : Switch? = null

    private var xMean: Double = 0.0
    private var yMean: Double = 0.0
    private var zMean: Double = 0.0

    private var filesWriteable: Boolean = false

    private val filePath: String = "DataStorage"
    private val fileName: String = "DataLog.csv"

    private var logger: File? = null
    private var fos: FileOutputStream? = null

    
    private var seriesX: LineGraphSeries<DataPoint>? = null
    private var seriesY: LineGraphSeries<DataPoint>? = null
    private var seriesZ: LineGraphSeries<DataPoint>? = null


    private var seriesClamped: LineGraphSeries<DataPoint>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN)

        setContentView(R.layout.activity_main)

        switch = findViewById(R.id.on_off_switch)

        graph = findViewById(R.id.graph)
        graph!!.viewport.isXAxisBoundsManual = true
        graph!!.viewport.setMinX(0.0)
        graph!!.viewport.setMaxX(40.0)

        graph!!.viewport.isYAxisBoundsManual = true
        graph!!.viewport.setMinY(-40.0)
        graph!!.viewport.setMaxY(40.0)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        filesWriteable = isExternalStorageAvailable() && !isExternalStorageReadOnly()

        if (filesWriteable) {
            logger = File(getExternalFilesDir(filePath), fileName)

            try {
                fos = FileOutputStream(logger)
                val header: String = "Time1,X,Y,Z,Normalized" + System.getProperty("line.separator")
                AppExecutors.getInstance().diskIO().execute(Runnable { fos!!.write(header.toByteArray()) })


            } catch (e: Exception) {
                Log.e(TAG, e.toString())
            }
        }

    }



    override
    fun onResume() {
        super.onResume()

        switch!!.setOnClickListener{
            if (switch!!.isChecked){
                initializeGraph()

                // register this class as a listener for the orientation and
                // accelerometer sensors
                sensorManager!!.registerListener(this,
                        sensorManager!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                        SensorManager.SENSOR_DELAY_NORMAL)

            }else{
                sensorManager!!.unregisterListener(this)
                AppExecutors.getInstance().diskIO().execute(Runnable {
                    val dataString = """Stopping Collection${System.getProperty("line.separator")}"""

                    fos!!.write(dataString.toByteArray())
                })

            }
        }

    }

    override fun onPause() {
        // unregister listener
        super.onPause()

        sensorManager!!.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        fos!!.close()
    }


    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(event: SensorEvent?) {
        if (event!!.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            getAccelerometer(event)
        }
    }


    private fun getAccelerometer(event: SensorEvent) {
        
        // obtain the raw accelerometer values
        val values = event.values
        val x = values[0]
        val y = values[1]
        val z = values[2]

        Log.d(TAG, "New Update")
        Log.d(TAG, "x = $x ")
        Log.d(TAG, "y = $y ")
        Log.d(TAG, "z = $z ")


        sampleCount += 1.0

        // plot the raw sensor data
        seriesX!!.appendData(DataPoint(sampleCount, x.toDouble()),
                true, 40)
        seriesY!!.appendData(DataPoint(sampleCount, y.toDouble()),
                true, 40)
        seriesZ!!.appendData(DataPoint(sampleCount, z.toDouble()),
                true, 40)


        val xPair = highPassFilterApproximation(sampleCount, x, xMean)
        val yPair = highPassFilterApproximation(sampleCount, y, yMean)
        val zPair = highPassFilterApproximation(sampleCount, z, zMean)

        val xFiltered = xPair.first
        xMean = xPair.second

        val yFiltered = yPair.first
        yMean = yPair.second

        val zFiltered = zPair.first
        zMean = zPair.second

        val filteredAccelerationMagnitude =
                Math.sqrt(xFiltered * xFiltered + yFiltered * yFiltered + zFiltered * zFiltered)

        var clamped: Double = 0.0
        if (filteredAccelerationMagnitude > 2.0) {
            clamped = 20.0
        }


        seriesClamped!!.appendData(DataPoint(sampleCount, clamped),
                true, 40)

        val actualTime = event.timestamp

        // save sample data to csv file
        AppExecutors.getInstance().diskIO().execute(Runnable {
            val dataString = "$actualTime, $x, $y, $z, $filteredAccelerationMagnitude" +
                    System.getProperty("line.separator")

            fos!!.write(dataString.toByteArray())
        })

    }


    private fun highPassFilterApproximation(count: Double, sample : Float, mean : Double) : Pair<Double, Double>{
        var gValue = sample.toDouble() / SensorManager.GRAVITY_EARTH
        var meanUpdate =  (mean * (count - 1) + gValue) / count
        gValue -=  meanUpdate

        return Pair(gValue, meanUpdate)
    }

    private fun isExternalStorageAvailable(): Boolean {
        val extStorageState = Environment.getExternalStorageState()
        return Environment.MEDIA_MOUNTED == extStorageState
    }

    private fun isExternalStorageReadOnly(): Boolean {
        val extStorageState = Environment.getExternalStorageState()
        return Environment.MEDIA_MOUNTED_READ_ONLY == extStorageState

    }

    private fun initializeGraph(){
        graph!!.removeAllSeries()
        seriesX = LineGraphSeries()
        seriesY = LineGraphSeries()
        seriesZ = LineGraphSeries()

        seriesX!!.color = Color.RED
        seriesY!!.color = Color.GREEN
        seriesZ!!.color = Color.BLUE

        seriesX!!.thickness = 4
        seriesY!!.thickness = 4
        seriesZ!!.thickness = 4

        seriesClamped = LineGraphSeries()
        seriesClamped!!.color = Color.BLACK
        seriesClamped!!.thickness = 8

        // add the raw sensor values to the graph
        graph!!.addSeries(seriesX)
        graph!!.addSeries(seriesY)
        graph!!.addSeries(seriesZ)

        // Binary shake indicator
        graph!!.addSeries(seriesClamped)

        seriesX!!.title = getString(R.string.x_sample)
        seriesY!!.title = getString(R.string.y_sample)
        seriesZ!!.title = getString(R.string.z_sample)
        seriesClamped!!.title = getString(R.string.shake_detect)
        graph!!.legendRenderer.isVisible = true
        graph!!.legendRenderer.align = LegendRenderer.LegendAlign.TOP


    }
}
