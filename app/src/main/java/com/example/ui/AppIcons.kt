package com.example.ui

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Lightweight, zero-overhead icons generated directly from vector paths.
// Replaces the 84 MB material-icons-extended library which inflated the APK with 11,400 unused classes.

private var _key: ImageVector? = null
val Icons.Filled.Key: ImageVector
    get() {
        if (_key != null) return _key!!
        _key = ImageVector.Builder(
            name = "Key",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(21.0f, 10.0f)
            horizontalLineToRelative(-8.35f)
            curveTo(11.83f, 7.67f, 9.61f, 6.0f, 7.0f, 6.0f)
            curveToRelative(-3.31f, 0f, -6.0f, 2.69f, -6.0f, 6.0f)
            reflectiveCurveToRelative(2.69f, 6.0f, 6.0f, 6.0f)
            curveToRelative(2.61f, 0f, 4.83f, -1.67f, 5.65f, -4.0f)
            horizontalLineTo(13.0f)
            lineToRelative(2f, 2f)
            lineToRelative(2f, -2.0f)
            lineToRelative(2f, 2f)
            lineToRelative(4.0f, -4.04f)
            lineTo(21.0f, 10.0f)
            close()
            moveTo(7.0f, 15.0f)
            curveToRelative(-1.65f, 0f, -3.0f, -1.35f, -3.0f, -3.0f)
            curveToRelative(0f, -1.65f, 1.35f, -3.0f, 3.0f, -3.0f)
            reflectiveCurveToRelative(3.0f, 1.35f, 3.0f, 3.0f)
            curveTo(10.0f, 13.65f, 8.65f, 15.0f, 7.0f, 15.0f)
            close()
        }.build()
        return _key!!
    }

private var _visibility: ImageVector? = null
val Icons.Filled.Visibility: ImageVector
    get() {
        if (_visibility != null) return _visibility!!
        _visibility = ImageVector.Builder(
            name = "Visibility",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(12.0f, 4.5f)
            curveTo(7.0f, 4.5f, 2.73f, 7.61f, 1f, 12.0f)
            curveToRelative(1.73f, 4.39f, 6.0f, 7.5f, 11.0f, 7.5f)
            reflectiveCurveToRelative(9.27f, -3.11f, 11.0f, -7.5f)
            curveToRelative(-1.73f, -4.39f, -6.0f, -7.5f, -11.0f, -7.5f)
            close()
            moveTo(12.0f, 17.0f)
            curveToRelative(-2.76f, 0f, -5.0f, -2.24f, -5.0f, -5.0f)
            reflectiveCurveToRelative(2.24f, -5.0f, 5.0f, -5.0f)
            reflectiveCurveToRelative(5.0f, 2.24f, 5.0f, 5.0f)
            reflectiveCurveToRelative(-2.24f, 5.0f, -5.0f, 5.0f)
            close()
            moveTo(12.0f, 9.0f)
            curveToRelative(-1.66f, 0f, -3.0f, 1.34f, -3.0f, 3.0f)
            reflectiveCurveToRelative(1.34f, 3.0f, 3.0f, 3.0f)
            reflectiveCurveToRelative(3.0f, -1.34f, 3.0f, -3.0f)
            reflectiveCurveToRelative(-1.34f, -3.0f, -3.0f, -3.0f)
            close()
        }.build()
        return _visibility!!
    }

private var _visibilityOff: ImageVector? = null
val Icons.Filled.VisibilityOff: ImageVector
    get() {
        if (_visibilityOff != null) return _visibilityOff!!
        _visibilityOff = ImageVector.Builder(
            name = "VisibilityOff",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(12.0f, 7.0f)
            curveToRelative(2.76f, 0f, 5.0f, 2.24f, 5.0f, 5.0f)
            curveToRelative(0f, 0.65f, -0.13f, 1.26f, -0.36f, 1.83f)
            lineToRelative(2.92f, 2.92f)
            curveToRelative(1.51f, -1.26f, 2.7f, -2.89f, 3.43f, -4.75f)
            curveToRelative(-1.73f, -4.39f, -6.0f, -7.5f, -11.0f, -7.5f)
            curveToRelative(-1.4f, 0f, -2.74f, 0.25f, -3.98f, 0.7f)
            lineToRelative(2.16f, 2.16f)
            curveTo(10.74f, 7.13f, 11.35f, 7.0f, 12.0f, 7.0f)
            close()
            moveTo(2f, 4.27f)
            lineToRelative(2.28f, 2.28f)
            lineToRelative(0.46f, 0.46f)
            curveTo(3.08f, 8.3f, 1.78f, 10.02f, 1f, 12.0f)
            curveToRelative(1.73f, 4.39f, 6.0f, 7.5f, 11.0f, 7.5f)
            curveToRelative(1.55f, 0f, 3.03f, -0.3f, 4.38f, -0.84f)
            lineToRelative(0.42f, 0.42f)
            lineTo(19.73f, 22.0f)
            lineTo(21.0f, 20.73f)
            lineTo(3.27f, 3.0f)
            lineTo(2f, 4.27f)
            close()
            moveTo(7.53f, 9.8f)
            lineToRelative(1.55f, 1.55f)
            curveToRelative(-0.05f, 0.21f, -0.08f, 0.43f, -0.08f, 0.65f)
            curveToRelative(0f, 1.66f, 1.34f, 3.0f, 3.0f, 3.0f)
            curveToRelative(0.22f, 0f, 0.44f, -0.03f, 0.65f, -0.08f)
            lineToRelative(1.55f, 1.55f)
            curveToRelative(-0.67f, 0.33f, -1.41f, 0.53f, -2.2f, 0.53f)
            curveToRelative(-2.76f, 0f, -5.0f, -2.24f, -5.0f, -5.0f)
            curveToRelative(0f, -0.79f, 0.2f, -1.53f, 0.53f, -2.2f)
            close()
            moveTo(11.84f, 9.02f)
            lineToRelative(3.15f, 3.15f)
            lineToRelative(0.02f, -0.16f)
            curveToRelative(0f, -1.66f, -1.34f, -3.0f, -3.0f, -3.0f)
            lineToRelative(-0.17f, 0.01f)
            close()
        }.build()
        return _visibilityOff!!
    }

private var _history: ImageVector? = null
val Icons.Filled.History: ImageVector
    get() {
        if (_history != null) return _history!!
        _history = ImageVector.Builder(
            name = "History",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(13.0f, 3.0f)
            curveToRelative(-4.97f, 0f, -9.0f, 4.03f, -9.0f, 9.0f)
            lineTo(1f, 12.0f)
            lineToRelative(3.89f, 3.89f)
            lineToRelative(0.07f, 0.14f)
            lineTo(9.0f, 12.0f)
            lineTo(6.0f, 12.0f)
            curveToRelative(0f, -3.87f, 3.13f, -7.0f, 7.0f, -7.0f)
            reflectiveCurveToRelative(7.0f, 3.13f, 7.0f, 7.0f)
            reflectiveCurveToRelative(-3.13f, 7.0f, -7.0f, 7.0f)
            curveToRelative(-1.93f, 0f, -3.68f, -0.79f, -4.94f, -2.06f)
            lineToRelative(-1.42f, 1.42f)
            curveTo(8.27f, 19.99f, 10.51f, 21.0f, 13.0f, 21.0f)
            curveToRelative(4.97f, 0f, 9.0f, -4.03f, 9.0f, -9.0f)
            reflectiveCurveToRelative(-4.03f, -9.0f, -9.0f, -9.0f)
            close()
            moveTo(12.0f, 8.0f)
            verticalLineToRelative(5.0f)
            lineToRelative(4.28f, 2.54f)
            lineToRelative(0.72f, -1.21f)
            lineToRelative(-3.5f, -2.08f)
            lineTo(13.5f, 8.0f)
            lineTo(12.0f, 8.0f)
            close()
        }.build()
        return _history!!
    }

private var _folderZip: ImageVector? = null
val Icons.Filled.FolderZip: ImageVector
    get() {
        if (_folderZip != null) return _folderZip!!
        _folderZip = ImageVector.Builder(
            name = "FolderZip",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(20.0f, 6.0f)
            horizontalLineToRelative(-8.0f)
            lineToRelative(-2.0f, -2.0f)
            horizontalLineTo(4.0f)
            curveTo(2.9f, 4.0f, 2.01f, 4.9f, 2.01f, 6.0f)
            lineTo(2f, 18.0f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(16.0f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2.0f)
            verticalLineTo(8.0f)
            curveTo(22.0f, 6.9f, 21.1f, 6.0f, 20.0f, 6.0f)
            close()
            moveTo(18.0f, 12.0f)
            horizontalLineToRelative(-2.0f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(-2.0f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(-2.0f)
            verticalLineToRelative(-2.0f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(-2.0f)
            horizontalLineToRelative(-2.0f)
            verticalLineToRelative(-2.0f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(-2.0f)
            horizontalLineToRelative(-2.0f)
            verticalLineTo(8.0f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(2f)
            verticalLineTo(12.0f)
            close()
        }.build()
        return _folderZip!!
    }

private var _segment: ImageVector? = null
val Icons.AutoMirrored.Filled.Segment: ImageVector
    get() {
        if (_segment != null) return _segment!!
        _segment = ImageVector.Builder(
            name = "Segment",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(9.0f, 18.0f)
            horizontalLineToRelative(12.0f)
            verticalLineToRelative(-2.0f)
            lineTo(9.0f, 16.0f)
            verticalLineToRelative(2f)
            close()
            moveTo(3.0f, 6.0f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(18.0f)
            lineTo(21.0f, 6.0f)
            lineTo(3.0f, 6.0f)
            close()
            moveTo(9.0f, 13.0f)
            horizontalLineToRelative(12.0f)
            verticalLineToRelative(-2.0f)
            lineTo(9.0f, 11.0f)
            verticalLineToRelative(2f)
            close()
        }.build()
        return _segment!!
    }

private var _terminal: ImageVector? = null
val Icons.Filled.Terminal: ImageVector
    get() {
        if (_terminal != null) return _terminal!!
        _terminal = ImageVector.Builder(
            name = "Terminal",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(20.0f, 4.0f)
            horizontalLineTo(4.0f)
            curveTo(2.89f, 4.0f, 2f, 4.9f, 2f, 6.0f)
            verticalLineToRelative(12.0f)
            curveToRelative(0f, 1.1f, 0.89f, 2f, 2f, 2f)
            horizontalLineToRelative(16.0f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2.0f)
            verticalLineTo(6.0f)
            curveTo(22.0f, 4.9f, 21.11f, 4.0f, 20.0f, 4.0f)
            close()
            moveTo(20.0f, 18.0f)
            horizontalLineTo(4.0f)
            verticalLineTo(8.0f)
            horizontalLineToRelative(16.0f)
            verticalLineTo(18.0f)
            close()
            moveTo(18.0f, 17.0f)
            horizontalLineToRelative(-6.0f)
            verticalLineToRelative(-2.0f)
            horizontalLineToRelative(6.0f)
            verticalLineTo(17.0f)
            close()
            moveTo(7.5f, 17.0f)
            lineToRelative(-1.41f, -1.41f)
            lineTo(8.67f, 13.0f)
            lineToRelative(-2.59f, -2.59f)
            lineTo(7.5f, 9.0f)
            lineToRelative(4.0f, 4.0f)
            lineTo(7.5f, 17.0f)
            close()
        }.build()
        return _terminal!!
    }

private var _contentCopy: ImageVector? = null
val Icons.Filled.ContentCopy: ImageVector
    get() {
        if (_contentCopy != null) return _contentCopy!!
        _contentCopy = ImageVector.Builder(
            name = "ContentCopy",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(16.0f, 1f)
            lineTo(4.0f, 1f)
            curveToRelative(-1.1f, 0f, -2.0f, 0.9f, -2.0f, 2f)
            verticalLineToRelative(14.0f)
            horizontalLineToRelative(2f)
            lineTo(4.0f, 3.0f)
            horizontalLineToRelative(12.0f)
            lineTo(16.0f, 1f)
            close()
            moveTo(19.0f, 5.0f)
            lineTo(8.0f, 5.0f)
            curveToRelative(-1.1f, 0f, -2.0f, 0.9f, -2.0f, 2f)
            verticalLineToRelative(14.0f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(11.0f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2.0f)
            lineTo(21.0f, 7.0f)
            curveToRelative(0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
            close()
            moveTo(19.0f, 21.0f)
            lineTo(8.0f, 21.0f)
            lineTo(8.0f, 7.0f)
            horizontalLineToRelative(11.0f)
            verticalLineToRelative(14.0f)
            close()
        }.build()
        return _contentCopy!!
    }

private var _android: ImageVector? = null
val Icons.Filled.Android: ImageVector
    get() {
        if (_android != null) return _android!!
        _android = ImageVector.Builder(
            name = "Android",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(17.6f, 9.48f)
            lineToRelative(1.84f, -3.18f)
            curveToRelative(0.16f, -0.31f, 0.04f, -0.69f, -0.26f, -0.85f)
            curveToRelative(-0.29f, -0.15f, -0.65f, -0.06f, -0.83f, 0.22f)
            lineToRelative(-1.88f, 3.24f)
            curveToRelative(-2.86f, -1.21f, -6.08f, -1.21f, -8.94f, 0f)
            lineTo(5.65f, 5.67f)
            curveToRelative(-0.19f, -0.29f, -0.58f, -0.38f, -0.87f, -0.2f)
            curveTo(4.5f, 5.65f, 4.41f, 6.01f, 4.56f, 6.3f)
            lineTo(6.4f, 9.48f)
            curveTo(3.3f, 11.25f, 1.28f, 14.44f, 1f, 18.0f)
            horizontalLineToRelative(22.0f)
            curveTo(22.72f, 14.44f, 20.7f, 11.25f, 17.6f, 9.48f)
            close()
            moveTo(7.0f, 15.25f)
            curveToRelative(-0.69f, 0f, -1.25f, -0.56f, -1.25f, -1.25f)
            curveToRelative(0f, -0.69f, 0.56f, -1.25f, 1.25f, -1.25f)
            reflectiveCurveTo(8.25f, 13.31f, 8.25f, 14.0f)
            curveTo(8.25f, 14.69f, 7.69f, 15.25f, 7.0f, 15.25f)
            close()
            moveTo(17.0f, 15.25f)
            curveToRelative(-0.69f, 0f, -1.25f, -0.56f, -1.25f, -1.25f)
            curveToRelative(0f, -0.69f, 0.56f, -1.25f, 1.25f, -1.25f)
            reflectiveCurveToRelative(1.25f, 0.56f, 1.25f, 1.25f)
            curveTo(18.25f, 14.69f, 17.69f, 15.25f, 17.0f, 15.25f)
            close()
        }.build()
        return _android!!
    }

private var _download: ImageVector? = null
val Icons.Filled.Download: ImageVector
    get() {
        if (_download != null) return _download!!
        _download = ImageVector.Builder(
            name = "Download",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(5.0f, 20.0f)
            horizontalLineToRelative(14.0f)
            verticalLineToRelative(-2.0f)
            horizontalLineTo(5.0f)
            verticalLineTo(20.0f)
            close()
            moveTo(19.0f, 9.0f)
            horizontalLineToRelative(-4.0f)
            verticalLineTo(3.0f)
            horizontalLineTo(9.0f)
            verticalLineToRelative(6.0f)
            horizontalLineTo(5.0f)
            lineToRelative(7.0f, 7.0f)
            lineTo(19.0f, 9.0f)
            close()
        }.build()
        return _download!!
    }

private var _hourglassEmpty: ImageVector? = null
val Icons.Filled.HourglassEmpty: ImageVector
    get() {
        if (_hourglassEmpty != null) return _hourglassEmpty!!
        _hourglassEmpty = ImageVector.Builder(
            name = "HourglassEmpty",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(6.0f, 2f)
            verticalLineToRelative(6.0f)
            horizontalLineToRelative(0.01f)
            lineTo(6.0f, 8.01f)
            lineTo(10.0f, 12.0f)
            lineToRelative(-4.0f, 4.0f)
            lineToRelative(0.01f, 0.01f)
            lineTo(6.0f, 16.01f)
            lineTo(6.0f, 22.0f)
            horizontalLineToRelative(12.0f)
            verticalLineToRelative(-5.99f)
            horizontalLineToRelative(-0.01f)
            lineTo(18.0f, 16.0f)
            lineToRelative(-4.0f, -4.0f)
            lineToRelative(4.0f, -3.99f)
            lineToRelative(-0.01f, -0.01f)
            lineTo(18.0f, 8.0f)
            lineTo(18.0f, 2f)
            lineTo(6.0f, 2f)
            close()
            moveTo(16.0f, 16.5f)
            lineTo(16.0f, 20.0f)
            lineTo(8.0f, 20.0f)
            verticalLineToRelative(-3.5f)
            lineToRelative(4.0f, -4.0f)
            lineToRelative(4.0f, 4.0f)
            close()
            moveTo(12.0f, 11.5f)
            lineToRelative(-4.0f, -4.0f)
            lineTo(8.0f, 4.0f)
            horizontalLineToRelative(8.0f)
            verticalLineToRelative(3.5f)
            lineToRelative(-4.0f, 4.0f)
            close()
        }.build()
        return _hourglassEmpty!!
    }

private var _error: ImageVector? = null
val Icons.Filled.Error: ImageVector
    get() {
        if (_error != null) return _error!!
        _error = ImageVector.Builder(
            name = "Error",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(12.0f, 2f)
            curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12.0f)
            reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
            reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
            reflectiveCurveTo(17.52f, 2f, 12.0f, 2f)
            close()
            moveTo(13.0f, 17.0f)
            horizontalLineToRelative(-2.0f)
            verticalLineToRelative(-2.0f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(2f)
            close()
            moveTo(13.0f, 13.0f)
            horizontalLineToRelative(-2.0f)
            lineTo(11.0f, 7.0f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(6.0f)
            close()
        }.build()
        return _error!!
    }

private var _schedule: ImageVector? = null
val Icons.Filled.Schedule: ImageVector
    get() {
        if (_schedule != null) return _schedule!!
        _schedule = ImageVector.Builder(
            name = "Schedule",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(11.99f, 2f)
            curveTo(6.47f, 2f, 2f, 6.48f, 2f, 12.0f)
            reflectiveCurveToRelative(4.47f, 10.0f, 9.99f, 10.0f)
            curveTo(17.52f, 22.0f, 22.0f, 17.52f, 22.0f, 12.0f)
            reflectiveCurveTo(17.52f, 2f, 11.99f, 2f)
            close()
            moveTo(12.0f, 20.0f)
            curveToRelative(-4.42f, 0f, -8.0f, -3.58f, -8.0f, -8.0f)
            reflectiveCurveToRelative(3.58f, -8.0f, 8.0f, -8.0f)
            reflectiveCurveToRelative(8.0f, 3.58f, 8.0f, 8.0f)
            reflectiveCurveToRelative(-3.58f, 8.0f, -8.0f, 8.0f)
            close()
            moveTo(12.5f, 7.0f)
            horizontalLineTo(11.0f)
            verticalLineToRelative(6.0f)
            lineToRelative(5.25f, 3.15f)
            lineToRelative(0.75f, -1.23f)
            lineToRelative(-4.5f, -2.67f)
            close()
        }.build()
        return _schedule!!
    }

val Icons.Filled.Segment: ImageVector
    get() = Icons.AutoMirrored.Filled.Segment

private var _arrowBack: ImageVector? = null
val Icons.AutoMirrored.Filled.ArrowBack: ImageVector
    get() {
        if (_arrowBack != null) return _arrowBack!!
        _arrowBack = ImageVector.Builder(
            name = "ArrowBack",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(20.0f, 11.0f)
            horizontalLineTo(7.83f)
            lineToRelative(5.59f, -5.59f)
            lineTo(12.0f, 4.0f)
            lineToRelative(-8.0f, 8.0f)
            lineToRelative(8.0f, 8.0f)
            lineToRelative(1.41f, -1.41f)
            lineTo(7.83f, 13.0f)
            horizontalLineTo(20.0f)
            verticalLineToRelative(-2.0f)
            close()
        }.build()
        return _arrowBack!!
    }

private var _checkCircle: ImageVector? = null
val Icons.Filled.CheckCircle: ImageVector
    get() {
        if (_checkCircle != null) return _checkCircle!!
        _checkCircle = ImageVector.Builder(
            name = "CheckCircle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(12.0f, 2f)
            curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12.0f)
            reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
            reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
            reflectiveCurveTo(17.52f, 2f, 12.0f, 2f)
            close()
            moveTo(10.0f, 17.0f)
            lineToRelative(-5.0f, -5.0f)
            lineToRelative(1.41f, -1.41f)
            lineTo(10.0f, 14.17f)
            lineToRelative(7.59f, -7.59f)
            lineTo(19.0f, 8.0f)
            lineToRelative(-9.0f, 9.0f)
            close()
        }.build()
        return _checkCircle!!
    }

private var _keyboardArrowDown: ImageVector? = null
val Icons.Filled.KeyboardArrowDown: ImageVector
    get() {
        if (_keyboardArrowDown != null) return _keyboardArrowDown!!
        _keyboardArrowDown = ImageVector.Builder(
            name = "KeyboardArrowDown",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(7.41f, 8.59f)
            lineTo(12.0f, 13.17f)
            lineToRelative(4.59f, -4.58f)
            lineTo(18.0f, 10.0f)
            lineToRelative(-6.0f, 6.0f)
            lineToRelative(-6.0f, -6.0f)
            lineToRelative(1.41f, -1.41f)
            close()
        }.build()
        return _keyboardArrowDown!!
    }

private var _keyboardArrowUp: ImageVector? = null
val Icons.Filled.KeyboardArrowUp: ImageVector
    get() {
        if (_keyboardArrowUp != null) return _keyboardArrowUp!!
        _keyboardArrowUp = ImageVector.Builder(
            name = "KeyboardArrowUp",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(7.41f, 15.41f)
            lineTo(12.0f, 10.83f)
            lineToRelative(4.59f, 4.58f)
            lineTo(18.0f, 14.0f)
            lineToRelative(-6.0f, -6.0f)
            lineToRelative(-6.0f, 6.0f)
            close()
        }.build()
        return _keyboardArrowUp!!
    }

private var _refresh: ImageVector? = null
val Icons.Filled.Refresh: ImageVector
    get() {
        if (_refresh != null) return _refresh!!
        _refresh = ImageVector.Builder(
            name = "Refresh",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(17.65f, 6.35f)
            curveTo(16.2f, 4.9f, 14.21f, 4.0f, 12.0f, 4.0f)
            curveToRelative(-4.42f, 0f, -7.99f, 3.58f, -7.99f, 8.0f)
            reflectiveCurveToRelative(3.57f, 8.0f, 7.99f, 8.0f)
            curveToRelative(3.73f, 0f, 6.84f, -2.55f, 7.73f, -6.0f)
            horizontalLineToRelative(-2.08f)
            curveToRelative(-0.82f, 2.33f, -3.04f, 4.0f, -5.65f, 4.0f)
            curveToRelative(-3.31f, 0f, -6.0f, -2.69f, -6.0f, -6.0f)
            reflectiveCurveToRelative(2.69f, -6.0f, 6.0f, -6.0f)
            curveToRelative(1.66f, 0f, 3.14f, 0.69f, 4.22f, 1.78f)
            lineTo(13.0f, 11.0f)
            horizontalLineToRelative(7.0f)
            verticalLineTo(4.0f)
            lineToRelative(-2.35f, 2.35f)
            close()
        }.build()
        return _refresh!!
    }

private var _delete: ImageVector? = null
val Icons.Filled.Delete: ImageVector
    get() {
        if (_delete != null) return _delete!!
        _delete = ImageVector.Builder(
            name = "Delete",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(6.0f, 19.0f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(8.0f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2.0f)
            verticalLineTo(7.0f)
            horizontalLineTo(6.0f)
            verticalLineToRelative(12.0f)
            close()
            moveTo(19.0f, 4.0f)
            horizontalLineToRelative(-3.5f)
            lineToRelative(-1.0f, -1.0f)
            horizontalLineToRelative(-5.0f)
            lineToRelative(-1.0f, 1f)
            horizontalLineTo(5.0f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(14.0f)
            verticalLineTo(4.0f)
            close()
        }.build()
        return _delete!!
    }
