package io.kasava.utilities;

import android.content.Context;
import android.location.Location;
import android.util.Log;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import io.kasava.dashcampro.KdcService;
import io.kasava.dashcampro.R;
import io.kasava.data.Event;
import io.kasava.data.Model;

public class Events {
    private static final String TAG = "Events";

    private final Utilities utilities;

    public Events(Context context) {
        utilities = new Utilities(context);
        utilities.setModel();
        mModel = utilities.getModel();
    }

    //private static Model.TYPE mModelType;
    private static Model.MODEL mModel;

    public Event eventTrigger(final KdcService.EVENT_TYPE eventType, final Date eventDateTime, final List<Location> locationBuffer) {
        Log.d(TAG, "eventTrigger()::" + eventType);

        // Create new event
        Event event = new Event();

        if(mModel == Model.MODEL.KXB1 || mModel == Model.MODEL.KXB2) {
            Log.d(TAG, "eventTrigger()::Cancel all KXB1 or KXB2 events");
            return event;
        }

        event.dateTime = utilities.dateAsUtcDateTimeString(eventDateTime);

        switch (eventType) {
            case CAM_FAIL:
                event.type = 1;
                break;
            case SD_UNMOUNTED:
                event.type = 2;
                break;
            case SHOCK:
                event.type = 10;
                break;
            case ACCELERATION:
                event.type = 11;
                break;
            case BRAKE:
                event.type = 12;
                break;
            case TURN:
                event.type = 13;
                break;
            case SPEEDING:
                event.type = 14;
                break;
            case ALERT:
                event.type = 15;
                break;
            case MANDOWN:
                event.type = 20;
                break;
            case ZIGZAG:
                event.type = 21;
                break;
            case EATING:
                event.type = 40;
                break;

            case MOBILE:
                event.type = 41;
                break;
            default:
                event.type = 0;
        }

        if(event.type == 10 && !isEventTrue(eventDateTime.getTime(), locationBuffer)) {
            event.type = 16;
        }

        return event;
    }

    private boolean isEventTrue(final long eventDateTs, final List<Location> locationBuffer) {
        boolean eventTrue = true;
        float minSpeed = 200, maxSpeed = 0;
        boolean hasLocationData = false;

        for(Location location: locationBuffer) {
            if(location.getTime() > eventDateTs-5000 && location.getTime() < eventDateTs+5000) {
                hasLocationData = true;
                if(location.getSpeed() < minSpeed) { minSpeed = location.getSpeed(); }
                if(location.getSpeed() > maxSpeed) { maxSpeed = location.getSpeed(); }
            }
        }

        // Filter out events where speed is constant and over 50kph (high speed bumps)
        if(hasLocationData && minSpeed > 50 && (maxSpeed-minSpeed < 15)) {
            eventTrue = false;
        }

        return eventTrue;
    }
}