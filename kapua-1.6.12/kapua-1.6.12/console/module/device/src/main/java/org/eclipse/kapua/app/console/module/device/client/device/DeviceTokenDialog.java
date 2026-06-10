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
import com.google.gwt.user.client.ui.TextBox;
import org.eclipse.kapua.app.console.module.api.shared.model.session.GwtSession;

public class DeviceTokenDialog extends Dialog {

    private final GwtSession currentSession;

    private TextBox tokenField;
    private Button copyBtn;
    private Button generateBtn;
    private Button cancelBtn;
    private Text statusText;

    public DeviceTokenDialog(GwtSession currentSession) {
        this.currentSession = currentSession;

        setHeading("Add Device");
        setModal(true);
        setResizable(false);
        setClosable(true);
        setHideOnButtonClick(false);
        setButtons("");
        setButtonAlign(HorizontalAlignment.CENTER);
        setWidth(480);
        setAutoHeight(true);
    }

    @Override
    protected void onRender(Element parent, int pos) {
        super.onRender(parent, pos);

        // Description
        Text desc = new Text("產生一次性配對 Token，將 Token 輸入裝置後即可完成配對。");
        desc.setStyleAttribute("display", "block");
        desc.setStyleAttribute("padding", "12px 16px 6px 16px");
        desc.setStyleAttribute("color", "#555");
        desc.setStyleAttribute("font-size", "13px");
        add(desc);

        // Token field row: [TextBox] [Copy Button]
        LayoutContainer tokenRow = new LayoutContainer();
        tokenRow.setStyleAttribute("padding", "6px 16px");
        TableLayout tl = new TableLayout(2);
        tokenRow.setLayout(tl);

        tokenField = new TextBox();
        tokenField.setReadOnly(true);
        tokenField.setWidth("370px");
        tokenField.getElement().getStyle().setProperty("fontFamily", "monospace");
        tokenField.getElement().getStyle().setProperty("fontSize", "12px");
        tokenField.getElement().getStyle().setProperty("padding", "5px 8px");
        tokenField.getElement().getStyle().setProperty("border", "1px solid #ccc");
        tokenField.getElement().getStyle().setProperty("borderRadius", "4px");
        tokenField.getElement().getStyle().setProperty("background", "#f8f9fa");
        tokenField.getElement().getStyle().setProperty("color", "#1a73e8");

        TableData tdField = new TableData();
        tdField.setPadding(2);
        tokenRow.add(tokenField, tdField);

        copyBtn = new Button("複製");
        copyBtn.setStyleAttribute("margin-left", "6px");
        copyBtn.setEnabled(false);
        copyBtn.addSelectionListener(new SelectionListener<ButtonEvent>() {
            @Override
            public void componentSelected(ButtonEvent ce) {
                String token = tokenField.getValue();
                if (token != null && !token.isEmpty()) {
                    copyToClipboard(token);
                    copyBtn.setText("✓ 已複製");
                    new Timer() {
                        @Override
                        public void run() {
                            copyBtn.setText("複製");
                        }
                    }.schedule(1500);
                }
            }
        });

        TableData tdCopy = new TableData();
        tdCopy.setPadding(2);
        tokenRow.add(copyBtn, tdCopy);

        add(tokenRow);

        // Status / error text
        statusText = new Text("");
        statusText.setStyleAttribute("display", "block");
        statusText.setStyleAttribute("padding", "4px 16px");
        statusText.setStyleAttribute("color", "red");
        statusText.setStyleAttribute("font-size", "12px");
        statusText.setStyleAttribute("min-height", "18px");
        add(statusText);

        // Buttons: Generate | Cancel
        generateBtn = new Button("產生 Token");
        generateBtn.addSelectionListener(new SelectionListener<ButtonEvent>() {
            @Override
            public void componentSelected(ButtonEvent ce) {
                generateToken();
            }
        });

        cancelBtn = new Button("取消");
        cancelBtn.addSelectionListener(new SelectionListener<ButtonEvent>() {
            @Override
            public void componentSelected(ButtonEvent ce) {
                hide();
            }
        });

        addButton(generateBtn);
        addButton(cancelBtn);
    }

    private void generateToken() {
        generateBtn.setEnabled(false);
        generateBtn.setText("產生中...");
        copyBtn.setEnabled(false);
        tokenField.setValue("");
        statusText.setText("");

        String scopeId = URL.encodePathSegment(currentSession.getSelectedAccountId());
        String apiUrl = "/v1/" + scopeId + "/devices/registrationToken";

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
                                String token = obj.get("value").isString().stringValue();
                                tokenField.setValue(token);
                                copyBtn.setEnabled(true);
                            } else {
                                statusText.setText("無效的回應格式");
                            }
                        } catch (Exception e) {
                            statusText.setText("解析回應失敗");
                        }
                    } else {
                        statusText.setText("產生失敗：HTTP " + response.getStatusCode());
                    }
                }

                @Override
                public void onError(Request request, Throwable exception) {
                    generateBtn.setEnabled(true);
                    generateBtn.setText("產生 Token");
                    statusText.setText("請求失敗：" + exception.getMessage());
                }
            });
        } catch (RequestException e) {
            generateBtn.setEnabled(true);
            generateBtn.setText("產生 Token");
            statusText.setText("請求失敗：" + e.getMessage());
        }
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
