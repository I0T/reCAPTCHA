package burp;

import java.util.ArrayList;
import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import custom.YunSu;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import custom.GUI;
import custom.myYunSu;

public class BurpExtender implements IBurpExtender, ITab, IContextMenuFactory, IIntruderPayloadGeneratorFactory,IIntruderPayloadGenerator
{	
	private GUI GUI;
    private static IBurpExtenderCallbacks callbacks;
    private IExtensionHelpers helpers;
    
    public PrintWriter stdout;//现在这里定义变量，再在registerExtenderCallbacks函数中实例化，如果都在函数中就只是局部变量，不能在这实例化，因为要用到其他参数。
    private String ExtenderName = "reCAPTCHA v0.1 by bit4";
    private String github = "https://github.com/bit4woo/reCAPTCHA";
	
	private String imgName;
    public IHttpRequestResponse imgMessageInfo;
    
    @Override
    public void registerExtenderCallbacks(IBurpExtenderCallbacks callbacks)
    {
    	stdout = new PrintWriter(callbacks.getStdout(), true);
    	stdout.println(ExtenderName);
    	stdout.println(github);
        this.callbacks = callbacks;
        helpers = callbacks.getHelpers();
        callbacks.setExtensionName(ExtenderName); //插件名称
        //callbacks.registerHttpListener(this); //如果没有注册，下面的processHttpMessage方法是不会生效的。处理请求和响应包的插件，这个应该是必要的
        callbacks.registerContextMenuFactory(this);
        callbacks.registerIntruderPayloadGeneratorFactory(this);
        addMenuTab();        
    }

/////////////////////////////////////////自定义函数/////////////////////////////////////////////////////////////
    public static IBurpExtenderCallbacks getBurpCallbacks() {
        return callbacks;
    }
    
    public static byte[] subBytes(byte[] src, int begin, int count) {
        byte[] bs = new byte[count];
        for (int i=begin; i<begin+count; i++) bs[i-begin] = src[i];
        return bs;
    }
    
	public String getHost(IRequestInfo analyzeRequest){
    	List<String> headers = analyzeRequest.getHeaders();
    	String domain = "";
    	for(String item:headers){
    		if (item.toLowerCase().contains("host")){
    			domain = new String(item.substring(6));
    		}
    	}
    	return domain ;
	}
	
	public String getImage(IHttpRequestResponse messageInfo) {
		if (messageInfo != null) {
			IHttpService service = messageInfo.getHttpService();
			byte[] request =  messageInfo.getRequest();
			IHttpRequestResponse messageInfo_issued = callbacks.makeHttpRequest(service,request);
			
			byte[] response = messageInfo_issued.getResponse();
			int BodyOffset = helpers.analyzeResponse(response).getBodyOffset();
			int body_length = response.length -BodyOffset;
			byte[] body = subBytes(response,BodyOffset,body_length);
			//这里之前遇到一个坑：现将byte[]转换为string，取substring后转换回来，这样是有问题的。
			//stdout.println("Response length:");
			//stdout.println(response.length);
			//stdout.println("offset");
			//stdout.println(BodyOffset);
			//stdout.println("body length");
			//stdout.println(body.length);
		
		    imgName = getHost(helpers.analyzeRequest(messageInfo))+System.currentTimeMillis()+".jpg";
		    //stdout.println(imgName);
		    try {
		    	File imageFile = new File(imgName);
		        //创建输出流  
		        FileOutputStream outStream = new FileOutputStream(imageFile);  
		        //写入数据  
				outStream.write(body);
				outStream.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} 
		    return imgName;
		}
		else {
			return null;
		}
	}
	
///////////////////////////////////自定义函数////////////////////////////////////////////////////////////
	
	
///////////////////////////////////以下是各种burp必须的方法 --start//////////////////////////////////////////
    public void addMenuTab()
    {
      SwingUtilities.invokeLater(new Runnable()
      {
        public void run()
        {
          BurpExtender.this.GUI = new GUI();
          BurpExtender.this.callbacks.addSuiteTab(BurpExtender.this); //这里的BurpExtender.this实质是指ITab对象，也就是getUiComponent()中的contentPane.这个参数由CGUI()函数初始化。
          //如果这里报java.lang.NullPointerException: Component cannot be null 错误，需要排查contentPane的初始化是否正确。
        }
      });
    }
	
	
    //ITab必须实现的两个方法
	@Override
	public String getTabCaption() {
		// TODO Auto-generated method stub
		return ("reCAPTCHA");
	}
	@Override
	public Component getUiComponent() {
		// TODO Auto-generated method stub
		return this.GUI;
	}

	
	@Override
	public List<JMenuItem> createMenuItems(IContextMenuInvocation invocation)
	{ //需要在签名注册！！callbacks.registerContextMenuFactory(this);
	    IHttpRequestResponse[] messages = invocation.getSelectedMessages();
	    List<JMenuItem> list = new ArrayList<JMenuItem>();
	    if((messages != null) && (messages.length ==1))
	    {	
	    	imgMessageInfo = messages[0];
	    	
	    	final byte[] sentRequestBytes = messages[0].getRequest();
	    	IRequestInfo analyzeRequest = helpers.analyzeRequest(sentRequestBytes);
	    	
	        JMenuItem menuItem = new JMenuItem("Send to reCAPTCHA");
	        menuItem.addActionListener(new ActionListener()
	        {
	          public void actionPerformed(ActionEvent e)
	          {
	            try
	            {	
	            	//stdout.println(new String(imgMessageInfo.getRequest()));
	            	GUI.MessageInfo = imgMessageInfo;
	            	
	            	GUI.imgRequestRaws.setText(new String(imgMessageInfo.getRequest())); //在GUI中显示这个请求信息。
	            	
	            	//IHttpService httpservice =imgMessageInfo.getHttpService();
	            	//String host = httpservice.getHost();
	            	//int port = httpservice.getPort();
	            	//String protocol = httpservice.getProtocol();
	            	
	            	
	            	GUI.imgHttpService.setText(imgMessageInfo.getHttpService().toString());
	            	
	            }
	            catch (Exception e1)
	            {
	                BurpExtender.this.callbacks.printError(e1.getMessage());
	            }
	          }
	        });
	        list.add(menuItem);
	    }
	    return list;
	}
	
	
	//IIntruderPayloadGeneratorFactory 所需实现的2个函数
	@Override
	public String getGeneratorName() {
		// TODO Auto-generated method stub
		return "reCAPTCHA";
	}

	@Override
	public IIntruderPayloadGenerator createNewInstance(IIntruderAttack attack) {
		// TODO Auto-generated method stub
		return this;
	}
	
	
	
	//IIntruderPayloadGenerator 所需实现的三个函数
	@Override
	public boolean hasMorePayloads() {
		// TODO Auto-generated method stub
		return true;
	}
	
	@Override
	public byte[] getNextPayload(byte[] baseValue) {
		// 获取图片验证码的值
		int times = 0;
		while(times <=5) {
			if (imgMessageInfo!=null) {
				String imgpath = getImage(imgMessageInfo);
				String paraString = GUI.APIRequestRaws.getText();
				String code = myYunSu.getCode(imgpath,paraString);
				stdout.println(imgpath+" "+code);
				return code.getBytes();
			}
			else {
				stdout.println("Failed try!!! please send image request to reCAPTCHA first!");
				times +=1;
				continue;
			}
		}
		return null;
	}

	@Override
	public void reset() {
		// TODO Auto-generated method stub
		
	}
	
//////////////////////////////////////////////各种burp必须的方法 --end//////////////////////////////////////////////////////////////
}