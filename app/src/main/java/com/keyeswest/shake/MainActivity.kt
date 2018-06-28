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
import android.view.View
import android.view.Window
import android.view.WindowManager
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import java.io.File
import java.io.FileOutputStream


class MainActivity : AppCompatActivity(), SensorEventListener {

    private val TAG = "MAIN_ACTIVITY"

    private var sensorManager: SensorManager? = null

    private var lastUpdate: Long = 0
    private var graph: GraphView? = null
    private var graph2LastXValue: Double = 0.0

    private var xMean: Double = 0.0
    private var yMean: Double = 0.0
    private var zMean: Double = 0.0

    private var filesWriteable: Boolean = false

    private val filePath: String = "DataStorage"
    private val fileName: String = "DataLog.csv"

    private var logger: File? = null
    private var fos: FileOutputStream? = null


    /*
      private val series : LineGraphSeries<DataPoint> =
              LineGraphSeries(arrayOf(DataPoint(0.0, 1.0),
                                      DataPoint(1.0, 5.0),
                                      DataPoint(2.0, 3.0),
                                      DataPoint(3.0, 2.0),
                                      DataPoint(4.0, 6.0)))
  */

    private var seriesX: LineGraphSeries<DataPoint> = LineGraphSeries()
    private var seriesY: LineGraphSeries<DataPoint> = LineGraphSeries()
    private var seriesZ: LineGraphSeries<DataPoint> = LineGraphSeries()

    private var seriesXPlot: LineGraphSeries<DataPoint> = LineGraphSeries()
    private var seriesYPlot: LineGraphSeries<DataPoint> = LineGraphSeries()
    private var seriesZPlot: LineGraphSeries<DataPoint> = LineGraphSeries()

    private var seriesNormalized: LineGraphSeries<DataPoint> = LineGraphSeries()

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)



        graph = findViewById(R.id.graph)
        graph!!.viewport.isXAxisBoundsManual = true
        graph!!.viewport.setMinX(0.0)
        graph!!.viewport.setMaxX(40.0)

        graph!!.viewport.isYAxisBoundsManual = true
        graph!!.viewport.setMinY(-40.0)
        graph!!.viewport.setMaxY(40.0)


        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lastUpdate = System.currentTimeMillis()

        seriesX.color = Color.RED
        seriesY.color = Color.GREEN
        seriesZ.color = Color.BLUE

        seriesX.thickness = 4
        seriesY.thickness = 4
        seriesZ.thickness = 4

        seriesXPlot.color = Color.RED
        seriesYPlot.color = Color.GREEN
        seriesZPlot.color = Color.BLUE

        seriesXPlot.thickness = 8
        seriesYPlot.thickness = 8
        seriesZPlot.thickness = 8

        seriesNormalized.color = Color.BLACK
        seriesNormalized.thickness = 8

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
        // register this class as a listener for the orientation and
        // accelerometer sensors
        sensorManager!!.registerListener(this,
                sensorManager!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_NORMAL)

        graph!!.addSeries(seriesX)
        graph!!.addSeries(seriesY)
        graph!!.addSeries(seriesZ)

        //    graph!!.addSeries(seriesXPlot)
        //    graph!!.addSeries(seriesYPlot)
        //    graph!!.addSeries(seriesZPlot)

        graph!!.addSeries(seriesNormalized)

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


    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event!!.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            getAccelerometer(event)
        }
    }


    private fun getAccelerometer(event: SensorEvent) {
        val values = event.values
        val x = values[0]
        val y = values[1]
        val z = values[2]

        Log.d(TAG, "New Update")
        Log.d(TAG, "x = $x ")
        Log.d(TAG, "y = $y ")
        Log.d(TAG, "z = $z ")


        graph2LastXValue += 1.0


        seriesX.appendData(DataPoint(graph2LastXValue, x.toDouble()),
                true, 40)
        seriesY.appendData(DataPoint(graph2LastXValue, y.toDouble()),
                true, 40)
        seriesZ.appendData(DataPoint(graph2LastXValue, z.toDouble()),
                true, 40)


        val xG = x.toDouble() / SensorManager.GRAVITY_EARTH
        val yG = y.toDouble() / SensorManager.GRAVITY_EARTH
        val zG = z.toDouble() / SensorManager.GRAVITY_EARTH

        xMean = (xMean * (graph2LastXValue - 1) + xG) / graph2LastXValue
        yMean = (yMean * (graph2LastXValue - 1) + yG) / graph2LastXValue
        zMean = (zMean * (graph2LastXValue - 1) + zG) / graph2LastXValue

        val xPlot = xG - xMean
        val yPlot = yG - yMean
        val zPlot = zG - zMean

        /*
        seriesXPlot.appendData(DataPoint(graph2LastXValue,  xPlot),
                true, 40)

        seriesYPlot.appendData(DataPoint(graph2LastXValue, yPlot),
                true, 40)

        seriesZPlot.appendData(DataPoint(graph2LastXValue, zPlot),
                true, 40)

*/

        val normalizedAcceleration = Math.sqrt(xPlot * xPlot + yPlot * yPlot + zPlot * zPlot)

        var clamped: Double = 0.0
        if (normalizedAcceleration > 2.0) {
            clamped = 20.0
        }


        Log.d(TAG, "Plot Value = $normalizedAcceleration")
        seriesNormalized.appendData(DataPoint(graph2LastXValue, clamped),
                true, 40)

        val actualTime = event.timestamp


        AppExecutors.getInstance().diskIO().execute(Runnable {
            val dataString = "$actualTime, $x, $y, $z, $normalizedAcceleration" +
                    System.getProperty("line.separator")

            fos!!.write(dataString.toByteArray())
        })

        if (normalizedAcceleration >= 2) {
            if (actualTime - lastUpdate < 200) {
                return
            }
            lastUpdate = actualTime
            // Toast.makeText(this, "Device was shuffled", Toast.LENGTH_SHORT)
            //         .show()
/*
            if (color) {
                view!!.setBackgroundColor(Color.GREEN)
            } else {
                view!!.setBackgroundColor(Color.RED)
            }
            color = !color
*/
        }

    }

    private fun isExternalStorageAvailable(): Boolean {
        val extStorageState = Environment.getExternalStorageState()
        return Environment.MEDIA_MOUNTED == extStorageState
    }

    private fun isExternalStorageReadOnly(): Boolean {
        val extStorageState = Environment.getExternalStorageState()
        return Environment.MEDIA_MOUNTED_READ_ONLY == extStorageState

    }
}