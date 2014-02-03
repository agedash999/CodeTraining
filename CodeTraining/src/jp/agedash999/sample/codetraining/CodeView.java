package jp.agedash999.sample.codetraining;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.AudioManager;
import android.media.SoundPool;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class CodeView
implements SurfaceHolder.Callback, Runnable {

	private SurfaceHolder holder;
	private Thread thread;
	private static Context context;
	private Bitmap whiteRect;
	private Bitmap lampR;
	private Bitmap lampB;
	private Bitmap parenthesis;
	private Paint paint = new Paint();
	private long baseTime;
	private int count;
	private SoundPool soundPool;
	private int bell;
	private int tick;

	//設定値
	//　テンポ ＝　一分間(60000mills)に四分音符を何回打つか
	//　600000 / テンポ ＝ １拍の長さ
	//private int tempo = 60000/96;
	private int tempo = 60000/140;
	private int rhythm = 4;

	//コード関連
	//	private Code[] codes;
	private List<Code> codelist = new ArrayList<Code>();
	private final int codeNumber = 4;

	public boolean twofiveFlag = false;

	//ループ処理のステータス
	private int status;
	private final int STOP = 0;
	private final int PREPARE_START = 1;
	private final int COUNT = 2;
	private final int PLAYING = 3;
	private final int PREPARE_STOP = 4;
	private final int ERROR = 999;

	//ログ関連
	private final String logTag = "CodeTraining.CodeView";

	//	private Random rnd = new Random();

	//画面表示位置関連
	private int canvasWidth;
	private int canvasHeight;
	private int[] width;
	private int[] height;
	private int[] srcX;
	private int[] srcY;
	private int[] dstX;
	private int[] dstY;
	private int lampSrcX;
	private int lampSrcY;
	private int lampDstX;
	private int lampDstY;

	private int bgColor = Color.DKGRAY;


	public CodeView(Context context, SurfaceView sv){
		CodeView.context = context;

		//SurfaceViewからholderを取得し、インターフェイスを設定する。
		holder = sv.getHolder();
		holder.addCallback( this );

		whiteRect = BitmapFactory.decodeResource
				(CodeView.context.getResources(), R.drawable.white);
		lampR =  BitmapFactory.decodeResource
				(CodeView.context.getResources(), R.drawable.lamp_r);
		lampB =  BitmapFactory.decodeResource
				(CodeView.context.getResources(), R.drawable.lamp_b);

		//画面表示配列の初期化
		width = new int[codeNumber];
		height = new int[codeNumber];
		srcX = new int[codeNumber];
		srcY = new int[codeNumber];
		dstX = new int[codeNumber];
		dstY = new int[codeNumber];

		//Threadの作成
		this.thread = new Thread(this);
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		// TODO 画面生成後の初期処理
		Canvas canvas = holder.lockCanvas();
		canvas.drawColor(bgColor);
		setScreenParm(canvas);
		holder.unlockCanvasAndPost(canvas);
		doDraw();
	}

	public void doOnResume(Context cont){
		soundPool = new SoundPool(2, AudioManager.STREAM_MUSIC, 0);
		bell = soundPool.load(cont, R.raw.bell, 0);
		tick = soundPool.load(cont, R.raw.tick, 0);
	}

	public void doOnPause(Context cont){
		stopPlaying();
		soundPool.release();
	}

	private void setScreenParm(Canvas canvas) {
		canvasWidth = canvas.getWidth();
		canvasHeight = canvas.getHeight();

		int baseWidth = canvasWidth/100;
		width[0] = baseWidth * 30;
		width[1] = baseWidth * 20;
		width[2] = baseWidth * 20;
		width[3] = baseWidth * 20;

		srcY[0] = baseWidth * 3;
		dstY[0] = srcY[0] + width[0];

		srcY[1] = dstY[0] + baseWidth * 3;
		dstY[1] = srcY[1] + width[1];

		srcY[2] = dstY[1] + baseWidth * 3;
		dstY[2] = srcY[2] + width[2];

		srcY[3] = dstY[2] + baseWidth * 3;
		dstY[3] = srcY[3] + width[3];

		lampSrcY = dstY[0] + baseWidth * 5;
		lampDstY = lampSrcY + baseWidth * 5;

		int baseHeight = canvasHeight/100;
		height[0] = baseHeight * 60;
		height[1] = baseHeight * 40;
		height[2] = baseHeight * 40;
		height[3] = baseHeight * 40;


		srcX[0] = ( canvasHeight - height[0] ) / 2 ;
		dstX[0] = srcX[0] + height[0] ;

		dstX[1] = dstX[0];
		srcX[1] = dstX[1] - height[1] ;

		dstX[2] = dstX[0];
		srcX[2] = dstX[2] - height[2] ;

		dstX[3] = dstX[0];
		srcX[3] = dstX[3] - height[3] ;

		lampSrcX = srcX[0] - baseWidth * 5;
		lampDstX = srcX[0];

	}

	@Override
	public void surfaceDestroyed(SurfaceHolder arg0) {
		// TODO 画面破棄時の処理
	}


	public void startPlaying(){
		//スレッド開始処理
		status = PREPARE_START;
		Log.d(logTag, "Status Chenged: PREPARE_START");
		switch (thread.getState()) {
		case WAITING:
			synchronized (thread) {
				thread.notify();
			}
			break;

		case NEW:
			thread.start();
			break;

		case RUNNABLE:
			thread.start();
			break;

		default:
			Log.e(logTag, "UnExpected block");
			//TODO スレッドのハンドリングについて、要検討
		}
	}

	public void stopPlaying(){
		status = PREPARE_STOP;
		Log.d(logTag, "Status Chenged: PREPARE_STOP");
	}

	public void clearCode(boolean refleshFlag){
		codelist.clear();
		if(refleshFlag){
			doDraw();
			Log.d(logTag, "clearCode");

		}
	}

	private Code createOneCode(Code prev){
		//コードを生成する
		CodeRoot root = null;
		CodeForm form = null;
		CodeTension[] tensions = null;
		if(prev == null || !twofiveFlag){
			//TODO 後ほど 設定に従って生成するロジック
			//Rootの生成

			int n = (int)(Math.random() * (EnumSet.allOf(CodeRoot.class).size()));
			root = CodeRoot.values()[n];
			n = (int)(Math.random() * (EnumSet.allOf(CodeForm.class).size()+1));
			if(n!=0){
				form = CodeForm.values()[n - 1];
			}
		}else{
			//TODO 後ほど 25フラグに応じて生成するロジック
			//TODO 後ほど 設定に従って生成するロジック
		}
		return new Code(root,form,tensions);
	}
	private void doDraw(){
		doDraw(lampR,0);
	}

	private void doDraw(Bitmap lamp,int alpha){
		//画面描画
		Canvas canvas = holder.lockCanvas();
		Rect srcRect = new Rect();
		Rect dstRect = new Rect();

		canvas.drawColor(bgColor);

		//コードの描画
		for(int i = 0; i < codeNumber ; i++){
			srcRect.set(0,0,whiteRect.getWidth(),whiteRect.getHeight());
			dstRect.set(srcY[i],srcX[i],dstY[i],dstX[i]);
			canvas.drawBitmap(whiteRect, srcRect, dstRect, null);
			if(i < codelist.size()){
				Code code = codelist.get(i);
				canvas.drawBitmap(code.root.Image(), srcRect, dstRect, null);
				if(code.form!=null){
					canvas.drawBitmap(code.form.Image(), srcRect, dstRect, null);
				}
				if(code.tensions!=null && code.tensions.length!=0){
					canvas.drawBitmap(parenthesis, srcRect, dstRect, null);
					for(CodeTension tension : code.tensions){
						canvas.drawBitmap(tension.Image(), srcRect, dstRect, null);
					}
				}
			}
		}
		//TODO ランプの描画
		srcRect.set(0,0,lamp.getWidth(),lamp.getHeight());
		dstRect.set(lampSrcY,lampSrcX,lampDstY,lampDstX);
		paint.setAlpha(alpha);
		canvas.drawBitmap(lamp,srcRect,dstRect, paint);


		holder.unlockCanvasAndPost(canvas);
	}

	@Override
	public void run() {
		// TODO ループ処理
		while(thread != null){
			if(status==STOP){

			}else if(status==PREPARE_START){
				//コード初期生成
				for(int i = codelist.size();i<codeNumber;i++){
					if(i<=0){
						codelist.add(i, createOneCode(null));
					}else{
						codelist.add(i, createOneCode(codelist.get(i-1)));
					}
				}
				//コード表示
				doDraw();
				//カウント開始
				status = COUNT;
				baseTime = System.currentTimeMillis();
				count = 0;
			}else if(status==COUNT){
				//カウント表示処理
				long time = System.currentTimeMillis();
				int alpha = 0;
				Bitmap lamp = null;
				int sound = 0;
				//最初のカウント
				if(count==0){
					lamp = lampR;
					alpha = 255;
					sound = tick;
					count = 1;
				}
				if(time < baseTime + tempo/2){
					//カウント更新なし
				}else if(time < baseTime + tempo){
					lamp = lampR;
					alpha = 50;
				}else if(count < rhythm){
					//カウント更新
					baseTime += tempo;
					count++;
					lamp = lampR;
					alpha = 255;
					sound = tick;
				}else{
					//カウント終了
					status = PLAYING;
					baseTime += tempo;
					count = 1;
					lamp = lampB;
					sound = bell;
					alpha = 255;
				}
				if(lamp!=null){
					if(sound!=0){
						soundPool.play(sound, 1.0F, 1.0F, 0, 0, 1.0F);
					}
					doDraw(lamp,alpha);
				}
			}else if(status==PLAYING){
				//カウント表示
				long time = System.currentTimeMillis();
				int alpha = 0;
				Bitmap lamp = null;
				int sound = 0;
				if(time < baseTime + tempo/2){
					//カウント更新なし
				}else if(time < baseTime + tempo){
					lamp = lampB;
					alpha = 50;
				}else if(count < rhythm){
					//カウント更新
					baseTime += tempo;
					count++;
					lamp = lampB;
					sound = tick;
					alpha = 255;
				}else{
					//次のコード
					//コード移動・追加生成
					codelist.remove(0);
					codelist.add(codeNumber-1,
							createOneCode(codelist.get(codeNumber-2)));
					baseTime += tempo;
					count = 1;
					lamp = lampB;
					sound = bell;
					alpha = 255;
				}
				if(lamp!=null){
					if(sound!=0){
						soundPool.play(sound, 1.0F, 1.0F, 0, 0, 1.0F);
					}
					doDraw(lamp,alpha);
				}

			}else if(status==PREPARE_STOP){
				synchronized (thread) {
					try {
						status = STOP;
						//画面の初期化
						doDraw();
						thread.wait();
					} catch (InterruptedException e) {
						// TODO 自動生成された catch ブロック
						status = ERROR;
						Log.e(logTag, "InterruptedException");
						e.printStackTrace();
					}
				}
			}
		}
	}

	@Override
	public void surfaceChanged
	(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
	}

	enum CodeRoot{
		C(R.drawable.c),
		F(R.drawable.f),
		Bf(R.drawable.bf),
		Ef(R.drawable.ef),
		Af(R.drawable.af),
		Cs(R.drawable.cs),
		Fs(R.drawable.fs),
		B(R.drawable.b),
		E(R.drawable.e),
		A(R.drawable.a),
		D(R.drawable.d),
		G(R.drawable.g);
		public final Bitmap image;
		public Bitmap Image(){return image;};

		private CodeRoot(int imageID){
			this.image = BitmapFactory.decodeResource
					(CodeView.context.getResources(), imageID);
		}
	}

	enum CodeForm{
		sevens(R.drawable.sevens),
		MajSevens(R.drawable.maj_sevens),
		MinSevens(R.drawable.min_sevens),
		MinMSevens(R.drawable.min_m_sevens),
		diminish(R.drawable.diminish);
		public final Bitmap image;
		public Bitmap Image(){return image;};

		private CodeForm(int imageID){
			this.image = BitmapFactory.decodeResource
					(CodeView.context.getResources(), imageID);
		}
	}

	enum CodeTension{
		//TODO テンションは未実装。
		;
		public final Bitmap image;
		public Bitmap Image(){return image;};

		private CodeTension(int imageID){
			this.image = BitmapFactory.decodeResource
					(CodeView.context.getResources(), imageID);
		}

	}

	class Code{
		public CodeRoot root;
		public CodeForm form;
		public CodeTension[] tensions;

		public Code(CodeRoot root,CodeForm form, CodeTension[] tensions){
			this.root = root;
			this.form = form;
			int i = 0;
			if(tensions!=null){
				for(CodeTension tension : tensions){
					tensions[i] = tension;
					i++;
				}
			}
		}
	}
}
