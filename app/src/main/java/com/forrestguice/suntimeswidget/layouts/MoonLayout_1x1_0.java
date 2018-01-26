/**
   Copyright (C) 2018 Forrest Guice
   This file is part of SuntimesWidget.

   SuntimesWidget is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   SuntimesWidget is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with SuntimesWidget.  If not, see <http://www.gnu.org/licenses/>.
*/

package com.forrestguice.suntimeswidget.layouts;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.util.TypedValue;
import android.widget.RemoteViews;

import com.forrestguice.suntimeswidget.R;
import com.forrestguice.suntimeswidget.SuntimesUtils;
import com.forrestguice.suntimeswidget.calculator.SuntimesMoonData;
import com.forrestguice.suntimeswidget.settings.WidgetSettings;
import com.forrestguice.suntimeswidget.themes.SuntimesTheme;

public class MoonLayout_1x1_0 extends MoonLayout
{
    public MoonLayout_1x1_0()
    {
        super();
    }

    public MoonLayout_1x1_0(int layoutID)
    {
        this.layoutID = layoutID;
    }

    @Override
    public void initLayoutID()
    {
        this.layoutID = R.layout.layout_widget_1x1_0;
    }

    @Override
    public void updateViews(Context context, int appWidgetId, RemoteViews views, SuntimesMoonData data)
    {
        super.updateViews(context, appWidgetId, views, data);
        boolean showSeconds = WidgetSettings.loadShowSecondsPref(context, appWidgetId);

        SuntimesUtils.TimeDisplayText riseString = utils.calendarTimeShortDisplayString(context, data.moonriseCalendarToday(), showSeconds);
        views.setTextViewText(R.id.text_time_rise, riseString.getValue());
        views.setTextViewText(R.id.text_time_rise_suffix, riseString.getSuffix());

        SuntimesUtils.TimeDisplayText setString = utils.calendarTimeShortDisplayString(context, data.moonsetCalendarToday(), showSeconds);
        views.setTextViewText(R.id.text_time_set, setString.getValue());
        views.setTextViewText(R.id.text_time_set_suffix, setString.getSuffix());
    }

    private int timeColor = Color.WHITE;

    @Override
    public void themeViews(Context context, RemoteViews views, SuntimesTheme theme)
    {
        super.themeViews(context, views, theme);

        int moonriseColor = theme.getSunriseTextColor();  // TODO
        int suffixColor = theme.getTimeSuffixColor();
        views.setTextColor(R.id.text_time_rise_suffix, suffixColor);
        views.setTextColor(R.id.text_time_rise, moonriseColor);

        int moonsetColor = theme.getSunsetTextColor(); // TODO
        views.setTextColor(R.id.text_time_set_suffix, suffixColor);
        views.setTextColor(R.id.text_time_set, moonsetColor);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
        {
            float timeSize = theme.getTimeSizeSp();
            float suffSize = theme.getTimeSuffixSizeSp();

            views.setTextViewTextSize(R.id.text_time_rise_suffix, TypedValue.COMPLEX_UNIT_SP, suffSize);
            views.setTextViewTextSize(R.id.text_time_rise, TypedValue.COMPLEX_UNIT_SP, timeSize);

            views.setTextViewTextSize(R.id.text_time_set, TypedValue.COMPLEX_UNIT_SP, timeSize);
            views.setTextViewTextSize(R.id.text_time_set_suffix, TypedValue.COMPLEX_UNIT_SP, suffSize);
        }

        Bitmap moonriseIcon = SuntimesUtils.insetDrawableToBitmap(context, R.drawable.ic_moonrise, theme.getSunriseIconColor(), theme.getSunriseIconStrokeColor(), theme.getSunriseIconStrokePixels(context));  // TODO: colors
        views.setImageViewBitmap(R.id.icon_time_sunrise, moonriseIcon);

        Bitmap moonsetIcon = SuntimesUtils.insetDrawableToBitmap(context, R.drawable.ic_moonset, theme.getSunsetIconColor(), theme.getSunsetIconStrokeColor(), theme.getSunsetIconStrokePixels(context));  // TODO: colors
        views.setImageViewBitmap(R.id.icon_time_sunset, moonsetIcon);
    }

    @Override
    public void prepareForUpdate(SuntimesMoonData data)
    {
        // EMPTY
    }
}
