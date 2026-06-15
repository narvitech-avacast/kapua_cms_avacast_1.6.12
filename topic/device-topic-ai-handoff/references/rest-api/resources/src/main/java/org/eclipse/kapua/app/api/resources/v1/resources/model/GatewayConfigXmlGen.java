package org.eclipse.kapua.app.api.resources.v1.resources.model;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.kapua.common.util.GatewayConfig.GatewayConfigModel;

public class GatewayConfigXmlGen {
    private Words rootElements;
    public GatewayConfigXmlGen() {
	rootElements = new Words();
    }
    public void setGatewayConfig(GatewayConfigModel model) {
	
	//set header
	rootElements.setValue(model.header);
	//set content
	List<Word> elements = new ArrayList<>();
	elements.add(genElement("ClientId",model.getDeviceName()));
	elements.add(genElement("AccountName",model.getAccountName()));
	elements.add(genElement("BrokerProtocol",model.getBrokerProtocol()));
	elements.add(genElement("BrokerUser",model.getBrokerUser()));
	elements.add(genElement("BrokerPassword",model.getBrokerPassword()));
	elements.add(genElement("BrokerHost",model.getBrokerHost()));
	elements.add(genElement("BrokerPort",model.getBrokerPort()));
	rootElements.setWords(elements);
	

	
    }

    public Words build() {
	return this.rootElements;
    }
    
    private Word genElement(String key,String value) {
	Word element = new Word();
	element.setKey(key);
	element.setValue(value);
	return element;
    }
}
