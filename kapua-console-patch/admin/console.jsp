<!--
	Copyright (c) 2016, 2022 Eurotech and/or its affiliates and others All rights reserved.

    This program and the accompanying materials are made
    available under the terms of the Eclipse Public License 2.0
    which is available at https://www.eclipse.org/legal/epl-2.0/

    SPDX-License-Identifier: EPL-2.0

    Contributors:
        Eurotech - initial API and implementation
-->

<!--
    ** READ ME **
    *************

	This is the original console.jsp index file.

    While developing is it possible to use this file,
    but any modification MUST be manually copied into console.jsp
    after minification of HTML.

    It has been minified in to improve page loading.
-->
<!doctype html>
<html lang="en">
    <head>
        <!--
            Meta tag informations
         -->
        <meta http-equiv="content-type" content="text/html; charset=UTF-8">
        <meta http-equiv="X-UA-Compatible" content="IE=9">

        <meta name="gwt:property" content="locale=en">

        <!--
            Favicon and title definition
         -->
        <title>Eclipse Kapua&trade; Console</title>
        <link rel="icon" type="image/ico" href="img/icon-color.ico"
    sizes="32x32" />
        <!--
            CSS resources
         -->
        <link rel="stylesheet" type="text/css" href="gxt-2.2.5/css/gxt-all.css" id="gxtCss">
        <link rel="stylesheet" type="text/css" id="gray" href="gxt-2.2.5/css/gxt-gray.css">

        <link rel="stylesheet" href="fontAwesome/css/font-awesome.min.css">

        <!--
            JS resources
         -->
        <script type="text/javascript" src="admin.nocache.js"></script>

        <!-- This script manage all JS/CSS deferred loading -->
        <script type="text/javascript" src="js/kapuaconsole/console.js" defer></script>

    </head>
    <body>
    <script>
    /* Auto-refresh every 3 s with no visible flicker.
       Strategy: snapshot grid rows as a fixed overlay before reload;
       hide GXT's white loading mask via CSS; fade out overlay when done. */
    (function() {
        var INTERVAL_MS = 3000;
        var _refreshing = false;

        // Make GXT loading mask invisible (keeps DOM detection working via offsetParent)
        var s = document.createElement('style');
        s.textContent = '.x-mask{opacity:0!important;background:transparent!important}' +
                        '.x-mask-msg{display:none!important}';
        document.head.appendChild(s);

        function findRefreshButton() {
            var btns = document.querySelectorAll('button');
            for (var i = 0; i < btns.length; i++) {
                var b = btns[i];
                if (!b.disabled && b.offsetParent !== null &&
                    b.innerHTML.indexOf('fa-refresh') !== -1) return b;
            }
            return null;
        }

        function tryAutoRefresh() {
            if (_refreshing) return;
            // Skip if any GXT modal window is visible
            var modals = document.querySelectorAll('.x-window');
            for (var m = 0; m < modals.length; m++) {
                if (modals[m].offsetParent !== null) return;
            }
            var btn = findRefreshButton();
            if (!btn) return;

            // Snapshot the grid data rows as a viewport-fixed overlay
            var gridBody = document.querySelector('.x-grid3-body');
            var overlay = null;
            if (gridBody && gridBody.children.length > 0) {
                var r = gridBody.getBoundingClientRect();
                overlay = document.createElement('div');
                overlay.innerHTML = gridBody.innerHTML;
                overlay.style.cssText =
                    'position:fixed;top:' + r.top + 'px;left:' + r.left + 'px;' +
                    'width:' + r.width + 'px;height:' + r.height + 'px;' +
                    'overflow:hidden;z-index:1000;background:#fff;pointer-events:none';
                document.body.appendChild(overlay);
            }

            _refreshing = true;
            btn.click();

            // Poll until GXT mask appears then disappears (= new data rendered)
            var maskSeen = false, ticks = 0;
            var poll = setInterval(function() {
                ticks++;
                var mask = document.querySelector('.x-mask');
                var maskOn = mask && mask.offsetParent !== null;
                if (maskOn) maskSeen = true;
                // Done: mask appeared+gone, OR no mask after 400ms, OR hard 5s timeout
                if ((maskSeen && !maskOn) || (!maskSeen && ticks > 8) || ticks > 100) {
                    clearInterval(poll);
                    _refreshing = false;
                    if (overlay) {
                        overlay.style.transition = 'opacity 0.2s ease-out';
                        overlay.style.opacity = '0';
                        setTimeout(function() {
                            if (overlay.parentNode) overlay.parentNode.removeChild(overlay);
                        }, 250);
                    }
                }
            }, 50);
        }

        // Wait 5 s for GWT to fully initialize, then start polling
        setTimeout(function() { setInterval(tryAutoRefresh, INTERVAL_MS); }, 5000);
    })();
    </script>
    </body>
</html>
