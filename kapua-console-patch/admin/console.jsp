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
    /* Incremental cell-only live update every 3 s.
       Polls /device-live-status.jsp for Status, Network/SSID, IP, AP, Signal;
       updates ONLY the changed cells in-place — no full grid reload, no flicker. */
    (function() {
        // DeviceGrid.java column indices (0-based):
        //  0:displayName  1:peerStatus  4:network  5:clientIp  6:hotSpotStatus  7:signal
        function cellDiv(row, idx) {
            var td = row.querySelector('td.x-grid3-col-' + idx);
            return td ? td.querySelector('div') : null;
        }

        function statusHtml(st) {
            var online = (st === 'CONNECTED');
            var c = online ? '0, 128, 0' : '128, 128, 128';
            return "<i class='fa fa-circle fa-lg' style='color:rgb(" + c + ")'></i>&nbsp;" +
                   (st || 'UNKNOWN');
        }

        function apHtml(ap) {
            var on = ap && ap.toLowerCase() === 'on';
            var c = on ? '0, 128, 0' : '128, 128, 128';
            return "<i class='fa fa-circle fa-lg' style='color:rgb(" + c + ")' title='" +
                   (on ? 'Online' : 'Offline') + "'></i>";
        }

        function applyUpdate(devices) {
            var rows = document.querySelectorAll('.x-grid3-body .x-grid3-row');
            if (!rows.length) return;

            // Index devices by displayName for O(1) row lookup
            var byName = {};
            for (var i = 0; i < devices.length; i++) byName[devices[i].dn] = devices[i];

            for (var r = 0; r < rows.length; r++) {
                var row = rows[r];
                var nameDiv = cellDiv(row, 0);
                if (!nameDiv) continue;
                var dev = byName[nameDiv.textContent.trim()];
                if (!dev) continue;

                // Status (col 1): icon + text
                var stDiv = cellDiv(row, 1);
                if (stDiv) stDiv.innerHTML = statusHtml(dev.st);

                // Network/SSID (col 4)
                var netDiv = cellDiv(row, 4);
                if (netDiv) netDiv.textContent = dev.ssid || 'Ethernet';

                // IP (col 5)
                var ipDiv = cellDiv(row, 5);
                if (ipDiv) ipDiv.textContent = dev.ip || '';

                // AP/HotSpot (col 6): only update when cache has data
                if (dev.ap !== '') {
                    var apDiv = cellDiv(row, 6);
                    if (apDiv) apDiv.innerHTML = apHtml(dev.ap);
                }

                // Signal (col 7)
                var sigDiv = cellDiv(row, 7);
                if (sigDiv) sigDiv.textContent = dev.sig ? dev.sig + ' dBm' : '';
            }
        }

        function poll() {
            // Skip while any GXT modal is open
            var modals = document.querySelectorAll('.x-window');
            for (var m = 0; m < modals.length; m++) {
                if (modals[m].offsetParent !== null) return;
            }

            var xhr = new XMLHttpRequest();
            xhr.open('GET', '/device-live-status.jsp', true);
            xhr.timeout = 2500;
            xhr.onload = function() {
                if (xhr.status === 200) {
                    try { applyUpdate(JSON.parse(xhr.responseText)); } catch (e) {}
                }
            };
            xhr.send();
        }

        // Wait 5 s for GWT to fully render the grid, then poll every 3 s
        setTimeout(function() { setInterval(poll, 3000); }, 5000);
    })();
    </script>
    </body>
</html>
