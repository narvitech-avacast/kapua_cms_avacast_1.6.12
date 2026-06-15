/*******************************************************************************
 * Copyright (c) 2024 Eurotech and/or its affiliates and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.kapua.app.console.module.device.client.device;

import com.extjs.gxt.ui.client.Style.HorizontalAlignment;
import com.extjs.gxt.ui.client.event.ButtonEvent;
import com.extjs.gxt.ui.client.event.SelectionListener;
import com.extjs.gxt.ui.client.widget.Dialog;
import com.extjs.gxt.ui.client.widget.LayoutContainer;
import com.extjs.gxt.ui.client.widget.Text;
import com.extjs.gxt.ui.client.widget.button.Button;
import com.extjs.gxt.ui.client.widget.layout.TableData;
import com.extjs.gxt.ui.client.widget.layout.TableLayout;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.http.client.URL;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.json.client.JSONValue;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import org.eclipse.kapua.app.console.module.api.shared.model.session.GwtSession;

public class DeviceTokenDialog extends Dialog {

    private final GwtSession currentSession;

    // Buttons declared as fields so they can be referenced after onRender
    private Button generateBtn;
    private Button cancelBtn;
    private LayoutContainer bodyArea;

    public DeviceTokenDialog(GwtSession currentSession) {
        this.currentSession = currentSession;

        setHeading("產生裝置配對 Token");
        setModal(true);
        setResizable(false);
        setClosable(true);
        setHideOnButtonClick(false);
        setButtons("");
        setButtonAlign(HorizontalAlignment.CENTER);
        setWidth(480);
        setAutoHeight(true);

        // Buttons MUST be added in constructor — adding after super.onRender() causes
        // them to be silently dropped because the GXT button bar is already rendered.
        cancelBtn = new Button("取消");
        cancelBtn.addSelectionListener(new SelectionListener<ButtonEvent>() {
            @Override
            public void componentSelected(ButtonEvent ce) {
                hide();
            }
        });

        generateBtn = new Button("產生 Token");
        generateBtn.addSelectionListener(new SelectionListener<ButtonEvent>() {
            @Override
            public void componentSelected(ButtonEvent ce) {
                generateToken();
            }
        });

        addButton(cancelBtn);
        addButton(generateBtn);
    }

    @Override
    protected void onRender(Element parent, int pos) {
        super.onRender(parent, pos);

        // Single container we can wipe and repopulate without touching the button bar
        bodyArea = new LayoutContainer();
        add(bodyArea);

        showInitialView();
    }

    // ── View: initial ────────────────────────────────────────────

    private void showInitialView() {
        bodyArea.removeAll();

        bodyArea.add(buildInfoBox(
                "系統會產生一組短效一次性 Token。裝置輸入後，回傳 Token 與自己的 Client ID，CMS 驗證成功才建立裝置。"));

        bodyArea.layout(true);
        syncSize();
    }

    // ── Token generation ─────────────────────────────────────────

    private void generateToken() {
        generateBtn.setEnabled(false);
        generateBtn.setText("產生中...");

        String scopeIdEnc = URL.encodePathSegment(currentSession.getSelectedAccountId());
        String apiUrl = getApiBaseUrl() + "/v1/" + scopeIdEnc + "/devices/registrationToken";

        RequestBuilder rb = new RequestBuilder(RequestBuilder.POST, apiUrl);
        rb.setHeader("Authorization", "Bearer " + currentSession.getTokenId());
        rb.setHeader("Accept", "application/json");
        rb.setHeader("Content-Type", "application/json");

        try {
            rb.sendRequest("", new RequestCallback() {
                @Override
                public void onResponseReceived(Request request, Response response) {
                    generateBtn.setEnabled(true);
                    generateBtn.setText("產生 Token");

                    if (response.getStatusCode() == Response.SC_OK) {
                        try {
                            JSONValue json = JSONParser.parseStrict(response.getText());
                            JSONObject obj = json.isObject();
                            if (obj != null && obj.get("value") != null
                                    && obj.get("value").isString() != null) {
                                showSuccessView(obj.get("value").isString().stringValue());
                            } else {
                                addError("無效的回應格式");
                            }
                        } catch (Exception e) {
                            addError("解析回應失敗");
                        }
                    } else {
                        addError("產生失敗：HTTP " + response.getStatusCode());
                    }
                }

                @Override
                public void onError(Request request, Throwable exception) {
                    generateBtn.setEnabled(true);
                    generateBtn.setText("產生 Token");
                    addError("請求失敗：" + exception.getMessage());
                }
            });
        } catch (RequestException e) {
            generateBtn.setEnabled(true);
            generateBtn.setText("產生 Token");
            addError("請求失敗：" + e.getMessage());
        }
    }

    private String getApiBaseUrl() {
        String consolePort = Window.Location.getPort();
        String hostName = Window.Location.getHostName();

        if ("8083".equals(consolePort)) {
            return "http://" + hostName;
        }
        if ("8445".equals(consolePort)) {
            return "https://" + hostName + ":8443";
        }
        return "";
    }

    // ── View: success ────────────────────────────────────────────

    private void showSuccessView(final String token) {
        bodyArea.removeAll();

        // ✓ Success header
        LayoutContainer successBox = new LayoutContainer();
        successBox.setStyleAttribute("background", "#e6f4ea");
        successBox.setStyleAttribute("border-radius", "8px");
        successBox.setStyleAttribute("padding", "16px");
        successBox.setStyleAttribute("margin", "16px 16px 8px 16px");
        successBox.setStyleAttribute("text-align", "center");

        Text title = new Text("✓ 配對 Token 已產生");
        title.setStyleAttribute("font-size", "15px");
        title.setStyleAttribute("font-weight", "bold");
        title.setStyleAttribute("color", "#188038");
        title.setStyleAttribute("display", "block");
        title.setStyleAttribute("margin-bottom", "6px");
        successBox.add(title);

        Text subtitle = new Text("請將以下資訊輸入裝置");
        subtitle.setStyleAttribute("font-size", "12px");
        subtitle.setStyleAttribute("color", "#555");
        subtitle.setStyleAttribute("display", "block");
        successBox.add(subtitle);
        bodyArea.add(successBox);

        // Token value box
        bodyArea.add(buildValueBox("配對 Token（一次性）", token));

        // Scope ID value box
        bodyArea.add(buildValueBox("Scope ID（帳號 ID）", currentSession.getSelectedAccountId()));

        // Footer: replace [取消][產生 Token] → [完成]
        getButtonBar().removeAll();
        Button doneBtn = new Button("完成");
        doneBtn.addSelectionListener(new SelectionListener<ButtonEvent>() {
            @Override
            public void componentSelected(ButtonEvent ce) {
                hide();
            }
        });
        getButtonBar().add(doneBtn);
        getButtonBar().layout();

        bodyArea.layout(true);
        syncSize();
    }

    // ── Error handling ───────────────────────────────────────────

    private void addError(String message) {
        LayoutContainer errorBox = new LayoutContainer();
        errorBox.setStyleAttribute("background", "#fce8e6");
        errorBox.setStyleAttribute("border", "1px solid #f5c6c2");
        errorBox.setStyleAttribute("border-radius", "7px");
        errorBox.setStyleAttribute("padding", "10px 14px");
        errorBox.setStyleAttribute("margin", "0 16px 8px 16px");

        Text errorText = new Text(message);
        errorText.setStyleAttribute("font-size", "12px");
        errorText.setStyleAttribute("color", "#c5221f");
        errorBox.add(errorText);
        bodyArea.add(errorBox);

        bodyArea.layout(true);
        syncSize();
    }

    // ── Widget builders ──────────────────────────────────────────

    private LayoutContainer buildInfoBox(String message) {
        LayoutContainer box = new LayoutContainer();
        box.setStyleAttribute("background", "#e8f0fe");
        box.setStyleAttribute("border", "1px solid #c5d9f8");
        box.setStyleAttribute("border-radius", "7px");
        box.setStyleAttribute("padding", "10px 14px");
        box.setStyleAttribute("margin", "16px");

        Text text = new Text(message);
        text.setStyleAttribute("font-size", "13px");
        text.setStyleAttribute("color", "#1a73e8");
        box.add(text);

        return box;
    }

    private LayoutContainer buildValueBox(String labelStr, final String value) {
        LayoutContainer box = new LayoutContainer();
        box.setStyleAttribute("background", "#f8f9fa");
        box.setStyleAttribute("border", "1px solid #dee2e6");
        box.setStyleAttribute("border-radius", "8px");
        box.setStyleAttribute("padding", "14px 16px");
        box.setStyleAttribute("margin", "0 16px 8px 16px");

        Text label = new Text(labelStr);
        label.setStyleAttribute("font-size", "11px");
        label.setStyleAttribute("color", "#888");
        label.setStyleAttribute("display", "block");
        label.setStyleAttribute("margin-bottom", "6px");
        box.add(label);

        LayoutContainer row = new LayoutContainer();
        row.setLayout(new TableLayout(2));

        Text val = new Text(value);
        val.setStyleAttribute("font-family", "Courier New, monospace");
        val.setStyleAttribute("font-size", "13px");
        val.setStyleAttribute("color", "#1a73e8");
        val.setStyleAttribute("word-break", "break-all");
        TableData tdVal = new TableData();
        tdVal.setWidth("380px");
        row.add(val, tdVal);

        final Button copyBtn = new Button("複製"); // 複製
        copyBtn.setStyleAttribute("margin-left", "8px");
        copyBtn.addSelectionListener(new SelectionListener<ButtonEvent>() {
            @Override
            public void componentSelected(ButtonEvent ce) {
                copyToClipboard(value);
                copyBtn.setText("✓ 已複製"); // ✓ 已複製
                new Timer() {
                    @Override
                    public void run() {
                        copyBtn.setText("複製"); // 複製
                    }
                }.schedule(1500);
            }
        });
        row.add(copyBtn, new TableData());
        box.add(row);

        return box;
    }

    private static native void copyToClipboard(String text) /*-{
        if ($wnd.navigator.clipboard) {
            $wnd.navigator.clipboard.writeText(text);
        } else {
            var el = $doc.createElement('textarea');
            el.value = text;
            el.style.position = 'fixed';
            el.style.opacity = '0';
            $doc.body.appendChild(el);
            el.select();
            $doc.execCommand('copy');
            $doc.body.removeChild(el);
        }
    }-*/;
}
