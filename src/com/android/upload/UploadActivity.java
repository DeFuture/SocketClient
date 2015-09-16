package com.android.upload;
import java.io.File;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.text.DecimalFormat;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.service.UploadLogService;
import com.android.socket.utils.FileUtils;
import com.android.socket.utils.StreamTool;


  
public class UploadActivity extends Activity {  
	private Double fileLenght;
	private DecimalFormat df = new DecimalFormat("######0.00");;
	public static final int UPLOADINFG = 1;
	public static final int NETWORK_OFF = 2;
	private static final int FILE_SELECT_CODE = 0;
	public File uploadFile;
	public int hisLong;
	
	private EditText address;  
	private EditText port;  
	
    private EditText filenameText;  
    private TextView resulView;  
    private ProgressBar uploadbar;  
    private UploadLogService logService;  
    private boolean start=true;
    private Handler handler = new Handler(){  
        @Override  
        public void handleMessage(Message msg) {  
            int length = msg.getData().getInt("size");  
            uploadbar.setProgress(length);  
            float num = (float)uploadbar.getProgress()/(float)uploadbar.getMax();  
            int result = (int)(num * 100);  
            resulView.setText(result+ "%");  
            if(uploadbar.getProgress()==uploadbar.getMax()){  
                Toast.makeText(UploadActivity.this, R.string.success, 1).show();  
            }  
        }  
    };  
      
    @Override  
    public void onCreate(Bundle savedInstanceState) {  
        super.onCreate(savedInstanceState);  
        setContentView(R.layout.main);  
          
        logService = new UploadLogService(this);  
        filenameText = (EditText)this.findViewById(R.id.filename); 
        address = (EditText)findViewById(R.id.address_et);
        port = (EditText)findViewById(R.id.port_et);
        filenameText.setText("/storage/emulated/0/2015_09_14_13_45_23.zip");
        uploadbar = (ProgressBar) this.findViewById(R.id.uploadbar);  
        resulView = (TextView)this.findViewById(R.id.result);  
        Button button =(Button)this.findViewById(R.id.button);  
        Button button1 =(Button)this.findViewById(R.id.stop); 
        button1 .setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				start=false;
				
			}
		});
        button.setOnClickListener(new View.OnClickListener() {  
            @Override  
            public void onClick(View v) {  
            	start=true;
                String filename = filenameText.getText().toString();  
                if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){  
//                    File uploadFile = new File(Environment.getExternalStorageDirectory(), filename);  
                	File uploadFile = new File(filename); 
                    if(uploadFile.exists()){  
                        uploadFile(uploadFile);  
                    }else{  
                        Toast.makeText(UploadActivity.this, R.string.filenotexsit, 1).show();  
                    }  
                }else{  
                    Toast.makeText(UploadActivity.this, R.string.sdcarderror, 1).show();  
                }  
            }  
        });  
        
        filenameText.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View arg0) {
				// TODO Auto-generated method stub
				showFileChooser();
			}
		});
    }  
    /** 
     * 上传文件 
     * @param uploadFile 
     */  
    private void uploadFile(final File uploadFile) {  
        new Thread(new Runnable() {           
            @Override  
            public void run() {  
                try {  
                    uploadbar.setMax((int)uploadFile.length());  
                    String souceid = logService.getBindId(uploadFile);  
                    String head = "Content-Length="+ uploadFile.length() + ";filename="+ uploadFile.getName() + ";sourceid="+  
                        (souceid==null? "" : souceid)+"\r\n";  
                    Socket socket;
                    if(!TextUtils.isEmpty(address.getText().toString()) && !TextUtils.isEmpty(port.getText().toString()))
                    	socket = new Socket(address.getText().toString(), Integer.parseInt(port.getText().toString()));  
                    else
                    	socket = new Socket("172.22.2.77",7070);  
                    OutputStream outStream = socket.getOutputStream();  
                    outStream.write(head.getBytes());  
                      
                    PushbackInputStream inStream = new PushbackInputStream(socket.getInputStream());      
                    String response = StreamTool.readLine(inStream);  
                    String[] items = response.split(";");  
                    String responseid = items[0].substring(items[0].indexOf("=")+1);  
                    String position = items[1].substring(items[1].indexOf("=")+1);  
                    if(souceid==null){//代表原来没有上传过此文件，往数据库添加一条绑定记录  
                        logService.save(responseid, uploadFile);  
                    }  
                    RandomAccessFile fileOutStream = new RandomAccessFile(uploadFile, "r");  
                    fileOutStream.seek(Integer.valueOf(position));  
                    byte[] buffer = new byte[1024];  
                    int len = -1;  
                    int length = Integer.valueOf(position);  
                    while(start&&(len = fileOutStream.read(buffer)) != -1){  
                        outStream.write(buffer, 0, len);  
                        length += len;  
                        Message msg = new Message();  
                        msg.getData().putInt("size", length);  
                        handler.sendMessage(msg);  
                    }  
                    fileOutStream.close();  
                    outStream.close();  
                    inStream.close();  
                    socket.close();  
                    if(length==uploadFile.length()) logService.delete(uploadFile);  
                } catch (Exception e) {  
                    e.printStackTrace();  
                }  
            }  
        }).start();  
    }  
    
    
    private void showFileChooser() {
	    Intent intent = new Intent(Intent.ACTION_GET_CONTENT); 
	    intent.setType("*/*"); 
	    intent.addCategory(Intent.CATEGORY_OPENABLE);
	 
	    try {
	        startActivityForResult( Intent.createChooser(intent, "Select a File to Upload"), FILE_SELECT_CODE);
	    } catch (android.content.ActivityNotFoundException ex) {
	        Toast.makeText(this, "Please install a File Manager.",  Toast.LENGTH_SHORT).show();
	    }
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)  {
	    switch (requestCode) {
	        case FILE_SELECT_CODE:      
	        if (resultCode == RESULT_OK) {  
	            // Get the Uri of the selected file 
	            Uri uri = data.getData();
	            String path = FileUtils.getPath(this, uri);
	            
//	            String path = Environment.getExternalStorageDirectory().toString()
//	    				+ File.separator + "360Download" + File.separator
//	    				+ "Demo.rar";
	    		uploadFile = new File(path);
	    		if (!uploadFile.exists())
	    		{
	    			Toast.makeText(UploadActivity.this, "文件不存在", Toast.LENGTH_SHORT).show();
	    			return;
	    		}
	    		filenameText.setText(path);
	    		fileLenght = ((double) uploadFile.length()) / (1024 * 1024);
//	    		tv_plan.setText("0MB / " + df.format(fileLenght) + "MB");
	    		uploadFile(uploadFile);
	        }           
	        break;
	    }
	super.onActivityResult(requestCode, resultCode, data);
	}
}  