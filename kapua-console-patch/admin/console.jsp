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

        <!-- Hide the username column in the Users grid (display name is sufficient) -->
        <style>
            /* Belt-and-suspenders: hide username inner div so even if JS misses a row the content is invisible */
            .x-grid3-hd-username, .x-grid3-col-username { visibility: hidden; font-size: 0; }
        </style>

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

    // When the user clicks the GXT paging-toolbar Refresh button (x-tbar-loading),
    // evict the server-side network-config cache BEFORE the grid RPC fires so that
    // findDeviceConfigurations() gets a cache miss and sends a fresh MQTT query.
    // We use event capture (runs before GXT's bubble handler) and a synchronous XHR
    // so the cache is guaranteed empty by the time the GWT-RPC call reaches the server.
    (function() {
        document.addEventListener('click', function(e) {
            var el = e.target;
            while (el && el !== document.body) {
                var cn = el.className;
                if (typeof cn === 'string' && cn.indexOf('x-tbar-loading') !== -1) {
                    var xhr = new XMLHttpRequest();
                    xhr.open('POST', 'network-refresh.jsp', false); // synchronous
                    xhr.withCredentials = true;
                    try { xhr.send(); } catch (ignore) {}
                    break;
                }
                el = el.parentElement;
            }
        }, true /* capture phase */);
    })();

    /* ── Auto-grant admin permissions (silent, idempotent) ── */
    (function() {
        var xhr = new XMLHttpRequest();
        xhr.open('POST', 'grant-admin.jsp', true);
        xhr.withCredentials = true;
        xhr.send();
    })();

    /* ── Users view: hide "username" column + inject Rename button ── */
    (function() {
        'use strict';
        var savedColIdx = -1;
        var colHidden   = false;
        var modal       = null;

        /* Zero-width a single <col> element */
        function collapseCol(table, idx) {
            if (idx < 0 || !table) return;
            var cols = table.querySelectorAll('colgroup col');
            if (cols[idx]) {
                cols[idx].style.setProperty('width', '0', 'important');
                cols[idx].style.setProperty('visibility', 'collapse', 'important');
            }
        }

        /* Make a <td> take no space */
        function hideTd(td) {
            if (!td) return;
            td.style.setProperty('width', '0', 'important');
            td.style.setProperty('min-width', '0', 'important');
            td.style.setProperty('max-width', '0', 'important');
            td.style.setProperty('overflow', 'hidden', 'important');
            td.style.setProperty('padding', '0', 'important');
            td.style.setProperty('border', 'none', 'important');
        }

        function hideUsernameCol() {
            var hdInner = document.querySelector('.x-grid3-hd-username');
            if (!hdInner) return false;

            var hdTd = hdInner.closest ? hdInner.closest('td') : hdInner.parentElement;
            if (!hdTd || !hdTd.parentElement) return false;

            /* Determine column index once */
            savedColIdx = Array.from(hdTd.parentElement.children).indexOf(hdTd);

            /* Hide header <td> + its <col> */
            hideTd(hdTd);
            collapseCol(hdTd.closest('table'), savedColIdx);

            /* Hide body cells and their <col>s */
            document.querySelectorAll('.x-grid3-col-username').forEach(function(inner) {
                hideTd(inner.closest ? inner.closest('td') : inner.parentElement);
            });
            document.querySelectorAll('.x-grid3-row table').forEach(function(tbl) {
                collapseCol(tbl, savedColIdx);
            });

            return true;
        }

        /* Apply column hiding to a newly added row */
        function hideColInNewRow(node) {
            if (!node || node.nodeType !== 1 || savedColIdx < 0) return;
            var inner = node.querySelector ? node.querySelector('.x-grid3-col-username') : null;
            if (inner) {
                hideTd(inner.closest ? inner.closest('td') : inner.parentElement);
                var tbl = inner.closest ? inner.closest('table') : null;
                collapseCol(tbl, savedColIdx);
            }
        }

        /* ── Rename button ── */
        function findUsersToolbar() {
            var hdInner = document.querySelector('.x-grid3-hd-username');
            if (!hdInner) return null;
            var grid = hdInner.closest ? hdInner.closest('.x-grid3') : null;
            if (!grid) return null;
            var el = grid.parentElement;
            for (var i = 0; i < 12; i++) {
                if (!el) break;
                var tb = el.querySelector('.x-toolbar-ct');
                if (tb) return tb;
                el = el.parentElement;
            }
            return null;
        }

        function getSelectedUser() {
            var row = document.querySelector('.x-grid3-row-selected');
            if (!row) return null;
            var unDiv = row.querySelector('.x-grid3-col-username');
            var dnDiv = row.querySelector('.x-grid3-col-displayName');
            return {
                username: unDiv ? unDiv.textContent.trim() : '',
                displayName: dnDiv ? dnDiv.textContent.trim() : ''
            };
        }

        function updateRenameBtn() {
            var btn = document.getElementById('kapua-user-rename-btn');
            if (!btn) return;
            var hasSelection = !!document.querySelector('.x-grid3-row-selected');
            btn.style.opacity = hasSelection ? '1' : '0.4';
            btn.style.cursor  = hasSelection ? 'pointer' : 'default';
        }

        function injectRenameBtn() {
            if (document.getElementById('kapua-user-rename-btn')) return;
            var tb = findUsersToolbar();
            if (!tb) return;
            var leftRow = tb.querySelector('td.x-toolbar-left table tbody tr');
            if (!leftRow) return;

            /* Separator */
            var sepTd = document.createElement('td');
            sepTd.className = 'x-toolbar-item';
            sepTd.innerHTML = '<table cellspacing="0" class="x-toolbar-separator">' +
                '<tbody><tr><td class="xtb-sep"></td></tr></tbody></table>';

            /* Button */
            var btnTd = document.createElement('td');
            btnTd.className = 'x-toolbar-item';
            btnTd.innerHTML =
                '<table id="kapua-user-rename-btn" cellspacing="0" class="x-btn x-btn-noicon">' +
                '<tbody class="x-btn-small x-btn-icon-small-left"><tr>' +
                '<td class="x-btn-ml"><i>&nbsp;</i></td>' +
                '<td class="x-btn-mc"><button class="x-btn-text" type="button">Rename</button></td>' +
                '<td class="x-btn-mr"><i>&nbsp;</i></td>' +
                '</tr></tbody></table>';

            leftRow.appendChild(sepTd);
            leftRow.appendChild(btnTd);

            var btn = document.getElementById('kapua-user-rename-btn');
            btn.style.opacity = '0.4';
            btn.addEventListener('click', function() {
                var user = getSelectedUser();
                if (!user || !user.username) return;
                showRenameModal(user.username, user.displayName);
            });
        }

        /* ── Modal dialog ── */
        function esc(s) {
            return (s || '').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
        }

        function showRenameModal(username, currentDisplayName) {
            if (modal) { modal.remove(); }

            modal = document.createElement('div');
            modal.style.cssText = 'position:fixed;top:0;left:0;width:100%;height:100%;' +
                'background:rgba(0,0,0,0.45);z-index:99999;display:flex;align-items:center;justify-content:center;';

            var box = document.createElement('div');
            box.style.cssText = 'background:#fff;border-radius:4px;padding:20px 24px;min-width:340px;' +
                'box-shadow:0 4px 20px rgba(0,0,0,0.35);font-family:Arial,sans-serif;font-size:13px;color:#333;';
            box.innerHTML =
                '<div style="font-size:15px;font-weight:bold;margin-bottom:14px;">Rename User</div>' +
                '<div style="margin-bottom:4px;color:#666;font-size:12px;">Username (read-only)</div>' +
                '<div style="margin-bottom:12px;padding:5px 8px;background:#f4f4f4;border:1px solid #ddd;border-radius:3px;font-size:12px;color:#888;">' + esc(username) + '</div>' +
                '<div style="margin-bottom:4px;">Display Name</div>' +
                '<input id="kapua-rename-input" type="text" value="' + esc(currentDisplayName) + '" ' +
                'style="width:100%;box-sizing:border-box;padding:6px 8px;border:1px solid #aaa;' +
                'border-radius:3px;font-size:13px;margin-bottom:4px;">' +
                '<div id="kapua-rename-error" style="color:#c00;font-size:12px;min-height:18px;margin-bottom:10px;"></div>' +
                '<div style="text-align:right;">' +
                '<button id="kapua-rename-cancel" style="margin-right:8px;padding:6px 16px;cursor:pointer;' +
                'border:1px solid #bbb;border-radius:3px;background:#f5f5f5;font-size:13px;">Cancel</button>' +
                '<button id="kapua-rename-ok" style="padding:6px 16px;cursor:pointer;' +
                'border:1px solid #3a87c1;border-radius:3px;background:#3a87c1;color:#fff;font-size:13px;">Save</button>' +
                '</div>';

            modal.appendChild(box);
            document.body.appendChild(modal);

            var input = document.getElementById('kapua-rename-input');
            input.focus(); input.select();

            document.getElementById('kapua-rename-cancel').onclick = closeModal;
            document.getElementById('kapua-rename-ok').onclick = function() {
                doRename(username, input.value);
            };
            input.addEventListener('keydown', function(e) {
                if (e.key === 'Enter')  doRename(username, input.value);
                if (e.key === 'Escape') closeModal();
            });
            modal.addEventListener('click', function(e) { if (e.target === modal) closeModal(); });
        }

        function closeModal() { if (modal) { modal.remove(); modal = null; } }

        function doRename(username, newDisplayName) {
            var errEl = document.getElementById('kapua-rename-error');
            var okBtn = document.getElementById('kapua-rename-ok');
            if (errEl) errEl.textContent = '';
            if (okBtn) { okBtn.disabled = true; okBtn.textContent = 'Saving…'; }

            var body = 'username=' + encodeURIComponent(username) +
                       '&displayName=' + encodeURIComponent(newDisplayName.trim());
            var xhr = new XMLHttpRequest();
            xhr.open('POST', 'user-rename.jsp', true);
            xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
            xhr.withCredentials = true;
            xhr.onload = function() {
                try {
                    var resp = JSON.parse(xhr.responseText);
                    if (resp.ok) {
                        /* Update the display-name cell immediately in the grid */
                        var dnDiv = document.querySelector('.x-grid3-row-selected .x-grid3-col-displayName');
                        if (dnDiv) dnDiv.textContent = newDisplayName.trim();
                        closeModal();
                    } else {
                        if (errEl) errEl.textContent = resp.error || 'Error';
                        if (okBtn) { okBtn.disabled = false; okBtn.textContent = 'Save'; }
                    }
                } catch (e) {
                    if (errEl) errEl.textContent = 'Unexpected error';
                    if (okBtn) { okBtn.disabled = false; okBtn.textContent = 'Save'; }
                }
            };
            xhr.onerror = function() {
                if (errEl) errEl.textContent = 'Network error';
                if (okBtn) { okBtn.disabled = false; okBtn.textContent = 'Save'; }
            };
            xhr.send(body);
        }

        /* ── MutationObserver: react to DOM changes ── */
        var observer = new MutationObserver(function(mutations) {
            var usersGridPresent = !!document.querySelector('.x-grid3-hd-username');

            if (usersGridPresent) {
                /* Hide column (once; re-hide new rows below) */
                if (!colHidden) {
                    colHidden = hideUsernameCol();
                }
                /* Inject rename button once the toolbar is rendered */
                injectRenameBtn();
                /* Keep rename button state in sync with row selection */
                updateRenameBtn();

                /* For each newly added node, hide its username cell */
                mutations.forEach(function(m) {
                    m.addedNodes.forEach(function(n) { hideColInNewRow(n); });
                });
            } else {
                /* Users view left — reset so we re-initialise on next visit */
                colHidden = false;
                savedColIdx = -1;
            }
        });

        observer.observe(document.body, {
            childList: true,
            subtree: true,
            attributes: true,
            attributeFilter: ['class']
        });
    })();
    </script>
    </body>
</html>
