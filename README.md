# KarazAccuCheckLibrary

### Step 1. Add the JitPack repository to your build file

```
allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
```
	
  
### Step 2. Add the dependency


```
classpath 'com.github.dcendents:android-maven-gradle-plugin:2.1'

dependencies {
	        implementation 'com.github.anilkumar7717:KarazAccuCheck-Library:1.0'
	}
```
	
  
### Step 3. Implement BlueToothActivity() to your Activity Class  

 
### Step 4. register Broadcast Receiver 
  
```registerReceiver(
            locationServiceStateReceiver,
            IntentFilter(LocationManager.MODE_CHANGED_ACTION)
        )
```
	
        
### Step 5. Overrider method to get glucoseMeasurement
 
``` override fun setGlucoseMeasurement(glucoseMeasurement: GlucoseMeasurement) {
        measurementValue.text = String.format(
            Locale.ENGLISH,
            "%.1f %s\n%s\n",
            glucoseMeasurement.value,
            if (glucoseMeasurement.unit === ObservationUnit.MmolPerLiter) "mmol/L" else "mg/dL",
            dateFormat.format(glucoseMeasurement.timestamp ?: Calendar.getInstance()),
        )
    }
```
