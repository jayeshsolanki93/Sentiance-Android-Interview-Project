Sentiance Android Interview Project

UPLOAD EDIT:
-----------------------------
I gave this interview in Feb 2018 and uploaded this here in June 2019. I got rejected because the implementation was flawed in terms of saving battery (I guess). I am not sharing the problem statement and if Sentiance is still using the same problem statement for their Android interview you shouldn't use this repo as a source of solution (mostly because it have major issues).

I am sharing it cause I spent more than 24 hours on this and in my phone interview I did not get any feedback to improve the implementation.
So I want to tip off other developers to not waste their time on such assignments.

-----------------------------

Design Approach

1. I haven't made the use of Google Play services for getting the location. I found that `FusedLocationProviderClient` which is a newer API to access location is efficient and it would have been much simpler to use, but it part of the Google Play services.
2. I am requesting location based on some criteria and not based on the location providers (GPS, Network). I have chose to keep the criteria as such that the result is of adequate accuracy and the power demand is also not high for fetching the result.
I have referred some articles to decide on the factors:
https://medium.com/@mizutori/tracking-highly-accurate-location-in-android-vol-1-ddbc757b045d
https://blog.codecentric.de/en/2014/05/android-gps-positioning-location-strategies/
3. For getting the current best estimate for the location I have used some code directly from the Developer's Guide
https://developer.android.com/guide/topics/location/strategies.html
4. I using Firebase JobDispatcher for scheduling the location fetching job in the background. JobService works ok in background on Android Oreo and survives reboots.
 
