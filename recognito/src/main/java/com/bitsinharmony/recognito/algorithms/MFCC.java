package com.bitsinharmony.recognito.algorithms;

/*
Kindly borrowed from here: https://sourceforge.net/p/spantus/svn/241/tree/trunk/spnt-math/src/main/java/klautau/
GPLv3: https://sourceforge.net/projects/spantus/
I have no idea if the implementation of MFCC here is correct
*/
/**Calculates the mel-based cepstra coefficients for one frame of speech.
 * Based on the original MFCC implementation described in:
 * [1] Davis & Mermelstein - IEEE Transactions on ASSP, August 1980.
 * Additional references are:
 * [2] Joseph Picone, Proceedings of the IEEE, Sep. 1993.
 * [3] Jankowski et al. IEEE Trans. on Speech and Audio Processing. July, 1995.
 * [4] Cardin et al, ICASSP'93 - pp. II-243
 *
 * Notice that there are several different implementations of the mel filter
 * bank. For example, the log is usually implementated after having the filter
 * outputs calculated, but could be implemented before filtering. Besides, there are
 * differences in the specification of the filter frequencies. [1]
 * suggested linear scale until 1000 Hz and logarithm scale afterwards.
 * This implementation uses the equation (10) in [2]:
 *      mel frequency = 2595 log(1 + (f/700)), where log is base 10
 * to find the filter bank center frequencies.
 *
 * @author Aldebaro Klautau
 * @version 2.0 - March 07, 2001
 */
// if m_oisZeroThCepstralCoefficientCalculated is true,
// this class decrements m_nnumberOfParameters by 1 and
// adds the 0-th coefficient to complete a vector with
// the number of MFCC's specified by the user.
public class MFCC {

    //parameter USEPOWER in HTK, where default is false
    private static final boolean m_ousePowerInsteadOfMagnitude = false;

    /**Number of MFCCs per speech frame.
     */
    private final int m_nnumberOfParameters;
    /**Sampling frequency.
     */
    private final double m_dsamplingFrequency;
    /**Number of filter in mel filter bank.
     */
    private final int m_nnumberOfFilters;
    /**Number of FFT points.
     */
    private final int m_nFFTLength;
    /**Coefficient of filtering performing in cepstral domain
     * (called 'liftering' operation). It is not used if
     * m_oisLifteringEnabled is false.
     */
    private final int m_nlifteringCoefficient;
    /**True enables liftering.
     */
    private final boolean m_oisLifteringEnabled;
    /**Minimum value of filter output, otherwise the log is not calculated
     * and m_dlogFilterOutputFloor is adopted.
     * ISIP implementation assumes m_dminimumFilterOutput = 1 and this value is used
     * here.
     */
    private final double m_dminimumFilterOutput = 1.0;

    /**True if the zero'th MFCC should be calculated.
     */
    private final boolean m_oisZeroThCepstralCoefficientCalculated;

    /**Floor value for filter output in log domain.
     * ISIP implementation assumes m_dlogFilterOutputFloor = 0 and this value is used
     * here.
     */
    private final double m_dlogFilterOutputFloor = 0.0;
    private int[][] m_nboundariesDFTBins;
    private double[][] m_dweights;
    private FFT m_fft;
    private double[][] m_ddCTMatrix;

    private double[] m_dfilterOutput;
    private final double[] m_nlifteringMultiplicationFactor;

    //things to be calculated just once:
    private final double m_dscalingFactor;

    /**The 0-th coefficient is included in nnumberOfParameters.
     * So, if one wants 12 MFCC's and additionally the 0-th
     * coefficient, one should call the constructor with
     * nnumberOfParameters = 13 and
     * oisZeroThCepstralCoefficientCalculated = true
     */
    public MFCC(int nnumberOfParameters,
                double dsamplingFrequency,
                int nnumberofFilters,
                int nFFTLength,
                boolean oisLifteringEnabled,
                int nlifteringCoefficient,
                boolean oisZeroThCepstralCoefficientCalculated) {

        m_oisZeroThCepstralCoefficientCalculated = oisZeroThCepstralCoefficientCalculated;
        if (m_oisZeroThCepstralCoefficientCalculated) {
            //the user shouldn't notice that nnumberOfParameters was
            //decremented internally
            m_nnumberOfParameters = nnumberOfParameters - 1;
        } else {
            m_nnumberOfParameters = nnumberOfParameters;
        }

        m_dsamplingFrequency = dsamplingFrequency;
        m_nnumberOfFilters = nnumberofFilters;
        m_nFFTLength = nFFTLength;

        //the filter bank weights, FFT's cosines and sines
        //and DCT matrix are initialized once to save computations.

        //initializes the mel-based filter bank structure
        calculateMelBasedFilterBank(dsamplingFrequency,
                nnumberofFilters,
                nFFTLength);
        m_fft = new FFT(m_nFFTLength); //initialize FFT
        initializeDCTMatrix();
        m_nlifteringCoefficient = nlifteringCoefficient;
        m_oisLifteringEnabled = oisLifteringEnabled;

        //avoid allocating RAM space repeatedly, m_dfilterOutput is
        //going to be used in method getParameters()
        m_dfilterOutput = new double[m_nnumberOfFilters];
		for (int i = 0; i < m_dfilterOutput.length; i++) {
			m_dfilterOutput[i]=0.0;
			
		}
 
        //needed in method getParameters()
        //m_dscalingFactor shouldn't be necessary because it's only
        //a scaling factor, but I'll implement it
        //for the sake of getting the same numbers ISIP gets
        m_dscalingFactor = Math.sqrt(2.0 / m_nnumberOfFilters);

        //for liftering method
        if (m_oisLifteringEnabled) {
            //note that:
//			int nnumberOfCoefficientsToLift = m_nnumberOfParameters; 
            //even when m_oisZeroThCepstralCoefficientCalculated is true
            //because if 0-th cepstral coefficient is included,
            //it is not liftered
            m_nlifteringMultiplicationFactor = new double[m_nlifteringCoefficient];
            double dfactor = m_nlifteringCoefficient / 2.0;
            double dfactor2 = Math.PI / m_nlifteringCoefficient;
            for (int i=0; i<m_nlifteringCoefficient; i++) {
                m_nlifteringMultiplicationFactor[i] = 1.0 + dfactor * Math.sin(dfactor2*(i+1));
            }
            if (m_nnumberOfParameters > m_nlifteringCoefficient) {
                throw new Error("Liftering is enabled and the number " +
                        "of parameters = " + m_nnumberOfParameters + ", while " +
                        "the liftering coefficient is " + m_nlifteringCoefficient +
                        ". In this case some cepstrum coefficients would be made " +
                        "equal to zero due to liftering, what does not make much " +
                        "sense in a speech recognition system. You may want to " +
                        "increase the liftering coefficient or decrease the number " +
                        "of MFCC parameters.");
            }
        } else {
            m_nlifteringMultiplicationFactor = null;
        }
    }

    /**Initializes the DCT matrix.*/
    private void initializeDCTMatrix() {
        m_ddCTMatrix = new double[m_nnumberOfParameters][m_nnumberOfFilters];
        for(int i=0;i<m_nnumberOfParameters;i++) {
            for(int j=0;j<m_nnumberOfFilters;j++) {
                m_ddCTMatrix[i][j] = Math.cos((i+1.0)*(j+1.0-0.5)*(Math.PI/m_nnumberOfFilters));
            }
        }
    }

    /**Converts frequencies in Hz to mel scale according to
     * mel frequency = 2595 log(1 + (f/700)), where log is base 10
     * and f is the frequency in Hz.
     */
    public static double[] convertHzToMel(double[] dhzFrequencies, double dsamplingFrequency) {
        double[] dmelFrequencies = new double[dhzFrequencies.length];
        for (int k=0; k<dhzFrequencies.length; k++) {
            dmelFrequencies[k] = 2595.0*( Math.log(1.0 + (dhzFrequencies[k] / 700.0) ) / Math.log(10) );
        }
        return dmelFrequencies;
    }

    /**Calculates triangular filters.
     */
    private void calculateMelBasedFilterBank(double dsamplingFrequency,
                                             int nnumberofFilters,
                                             int nfftLength) {

        //frequencies for each triangular filter
//		double[][] dfrequenciesInMelScale = new double[nnumberofFilters][3]; 
        //the +1 below is due to the sample of frequency pi (or fs/2)
        double[] dfftFrequenciesInHz = new double[nfftLength/2 + 1];
        //compute the frequency of each FFT sample (in Hz):
        double ddeltaFrequency = dsamplingFrequency / nfftLength;
        for (int i=0; i<dfftFrequenciesInHz.length; i++) {
            dfftFrequenciesInHz[i] = i * ddeltaFrequency;
        }
        //convert Hz to Mel
        double[] dfftFrequenciesInMel = MFCC.convertHzToMel(dfftFrequenciesInHz,dsamplingFrequency);

        //compute the center frequencies. Notice that 2 filters are
        //"artificially" created in the endpoints of the frequency
        //scale, correspondent to 0 and fs/2 Hz.
        double[] dfilterCenterFrequencies = new double[nnumberofFilters + 2];
        //implicitly: dfilterCenterFrequencies[0] = 0.0;
        ddeltaFrequency = dfftFrequenciesInMel[dfftFrequenciesInMel.length-1] / (nnumberofFilters+1);
        for (int i = 1; i < dfilterCenterFrequencies.length; i++) {
            dfilterCenterFrequencies[i] = i * ddeltaFrequency;
        }

        //initialize member variables
        m_nboundariesDFTBins= new int[m_nnumberOfFilters][2];
        m_dweights = new double[m_nnumberOfFilters][];

        //notice the loop starts from the filter i=1 because i=0 is the one centered at DC
        for (int i=1; i<= nnumberofFilters; i++) {
            m_nboundariesDFTBins[i-1][0] = Integer.MAX_VALUE;
            //notice the loop below doesn't include the first and last FFT samples
            for (int j=1; j<dfftFrequenciesInMel.length-1; j++) {
                //see if frequency j is inside the bandwidth of filter i
				
                if ( (dfftFrequenciesInMel[j] >= dfilterCenterFrequencies[i-1]) &
                        (dfftFrequenciesInMel[j] <= dfilterCenterFrequencies[i+1]) ) {
                    //the i-1 below is due to the fact that we discard the first filter i=0
                    //look for the first DFT sample for this filter
                    if (j < m_nboundariesDFTBins[i-1][0]) {
                        m_nboundariesDFTBins[i-1][0] = j;
                    }
                    //look for the last DFT sample for this filter
                    if (j > m_nboundariesDFTBins[i-1][1]) {
                        m_nboundariesDFTBins[i-1][1] = j;
                    }
                }
            }
        }
        //check for consistency. The problem below would happen just
        //in case of a big number of MFCC parameters for a small DFT length.
        for (int i=0; i< nnumberofFilters; i++) {
            if(m_nboundariesDFTBins[i][0]==m_nboundariesDFTBins[i][1]) {
                new Error("Error in MFCC filter bank. In filter "+i+" the first sample is equal to the last sample !" +
                        " Try changing some parameters, for example, decreasing the number of filters.");
            }
        }

        //allocate space
        for(int i=0;i<nnumberofFilters;i++) {
            m_dweights[i] = new double[m_nboundariesDFTBins[i][1]-m_nboundariesDFTBins[i][0]+1];
        }

        //calculate the weights
        for(int i=1;i<=nnumberofFilters;i++) {
            for(int j=m_nboundariesDFTBins[i-1][0],k=0;j<=m_nboundariesDFTBins[i-1][1];j++,k++) {
                if (dfftFrequenciesInMel[j] < dfilterCenterFrequencies[i]) {
                    m_dweights[i-1][k] = (dfftFrequenciesInMel[j]-dfilterCenterFrequencies[i-1]) /
                            (dfilterCenterFrequencies[i] - dfilterCenterFrequencies[i-1]);
                } else {
                    m_dweights[i-1][k] = 1.0 -( (dfftFrequenciesInMel[j]-dfilterCenterFrequencies[i]) /
                            (dfilterCenterFrequencies[i+1] - dfilterCenterFrequencies[i]) );
                }
            }
        }
    }

    /**Returns the MFCC coefficients for the given speech frame.
     * If calculated, the 0-th coefficient is added to the
     * end of the vector (for compatibility with HTK). The order
     * of an output vector x with 3 MFCC's, including the 0-th, would be:
     * x = {MFCC1, MFCC2, MFCC0}
     */
    public double[] getParameters(double[] fspeechFrame) {

        //use mel filter bank
        for(int i=0; i < m_nnumberOfFilters; i++) {
            m_dfilterOutput[i] = 0.0;
            //Notice that the FFT samples at 0 (DC) and fs/2 are not considered on this calculation
            if (m_ousePowerInsteadOfMagnitude) {
                double[] fpowerSpectrum = m_fft.calculateFFTPower(fspeechFrame);
                for(int j=m_nboundariesDFTBins[i][0], k=0;j<=m_nboundariesDFTBins[i][1];j++,k++) {
                    m_dfilterOutput[i] += fpowerSpectrum[j] * m_dweights[i][k];
                }
            } else {
                double[] fmagnitudeSpectrum = m_fft.calculateFFTMagnitude(fspeechFrame);
                for(int j=m_nboundariesDFTBins[i][0], k=0;j<=m_nboundariesDFTBins[i][1];j++,k++) {
                    m_dfilterOutput[i] += fmagnitudeSpectrum[j] * m_dweights[i][k];
                }
            }

            //ISIP (Mississipi univ.) implementation
            if (m_dfilterOutput[i] > m_dminimumFilterOutput) {//floor power to avoid log(0)
                m_dfilterOutput[i] = Math.log(m_dfilterOutput[i]); //using ln
            } else {
                m_dfilterOutput[i] = m_dlogFilterOutputFloor;
            }
        }

        //need to allocate space for output array
        //because it allows the user to call this method
        //many times, without having to do a deep copy
        //of the output vector
        double[] dMFCCParameters = null;
        if (m_oisZeroThCepstralCoefficientCalculated) {
            dMFCCParameters = new double[m_nnumberOfParameters + 1];
			for (int i = 0; i < dMFCCParameters.length; i++) {
				dMFCCParameters[i] = 0F;
			}
            //calculates zero'th cepstral coefficient and pack it
            //after the MFCC parameters of each frame for the sake
            //of compatibility with HTK
            double dzeroThCepstralCoefficient = 0.0;
            for(int j=0;j<m_nnumberOfFilters;j++) {
                dzeroThCepstralCoefficient += m_dfilterOutput[j];
            }
            dzeroThCepstralCoefficient *= m_dscalingFactor;
            dMFCCParameters[dMFCCParameters.length-1] = dzeroThCepstralCoefficient;
        } else {
            //allocate space
            dMFCCParameters = new double[m_nnumberOfParameters];
			for (int i = 0; i < dMFCCParameters.length; i++) {
				dMFCCParameters[i] = 0F;
			}
		} 
        //cosine transform
        for(int i=0;i<m_nnumberOfParameters;i++) {
            for(int j=0;j<m_nnumberOfFilters;j++) {
                dMFCCParameters[i] += m_dfilterOutput[j]*m_ddCTMatrix[i][j];
                //the original equations have the first index as 1
            }
            //could potentially incorporate liftering factor and
            //factor below to save multiplications, but will not
            //do it for the sake of clarity
            dMFCCParameters[i] *= m_dscalingFactor;
        }

        //debugging purposes
        //System.out.println("Windowed speech");
        //IO.DisplayVector(fspeechFrame);
        //System.out.println("FFT spectrum");
        //IO.DisplayVector(fspectrumMagnitude);
        //System.out.println("Filter output in dB");
        //IO.DisplayVector(dfilterOutput);
        //System.out.println("DCT matrix");
        //IO.DisplayMatrix(m_ddCTMatrix);
        //System.out.println("MFCC before liftering");
        //IO.DisplayVector(dMFCCParameters);

        if (m_oisLifteringEnabled) {
            // Implements liftering to smooth the cepstral coefficients according to
            // [1] Rabiner, Juang, Fundamentals of Speech Recognition, pp. 169,
            // [2] The HTK Book, pp 68 and
            // [3] ISIP package - Mississipi Univ. Picone's group.
            //if 0-th coefficient is included, it is not liftered
            for (int i=0; i<m_nnumberOfParameters; i++) {
                dMFCCParameters[i] *= m_nlifteringMultiplicationFactor[i];
            }
        }

        return dMFCCParameters;
    } //end method

    /**Returns the sampling frequency.
     */
    public double getSamplingFrequency() {
        return this.m_dsamplingFrequency;
    }

    /**Returns the number of points of the Fast Fourier
     * Transform (FFT) used in the calculation of this MFCC.
     */
    public int getFFTLength() {
        return m_nFFTLength;
    }

    /**Returns the number of MFCC coefficients,
     * including the 0-th if required by user in the object construction.
     */
    public int getNumberOfCoefficients() {
        return (m_oisZeroThCepstralCoefficientCalculated ? (m_nnumberOfParameters + 1) : m_nnumberOfParameters);
    }

    /**Return a string with all important parameters of this object.
     */
    public String toString() {
        return
                "MFCC.nnumberOfParameters = " + (m_oisZeroThCepstralCoefficientCalculated ? (m_nnumberOfParameters + 1) : m_nnumberOfParameters) +
                        "\n" + "MFCC.nnumberOfFilters = " + m_nnumberOfFilters +
                        "\n" + "MFCC.nFFTLength = " + m_nFFTLength +
                        "\n" + "MFCC.dsamplingFrequency = " + m_dsamplingFrequency +
                        "\n" + "MFCC.nlifteringCoefficient = " + m_nlifteringCoefficient +
                        "\n" + "MFCC.oisLifteringEnabled = " + m_oisLifteringEnabled +
                        "\n" + "MFCC.oisZeroThCepstralCoefficientCalculated = " + m_oisZeroThCepstralCoefficientCalculated;
    }

    public double[] getFilterBankOutputs(double[] fspeechFrame) {
        //use mel filter bank
        double dfilterOutput[] = new double[m_nnumberOfFilters];
        for(int i=0; i < m_nnumberOfFilters; i++) {
            //Notice that the FFT samples at 0 (DC) and fs/2 are not considered on this calculation
            if (m_ousePowerInsteadOfMagnitude) {
                double[] fpowerSpectrum = m_fft.calculateFFTPower(fspeechFrame);
                for(int j=m_nboundariesDFTBins[i][0], k=0;j<=m_nboundariesDFTBins[i][1];j++,k++) {
                    dfilterOutput[i] += fpowerSpectrum[j] * m_dweights[i][k];
                }
            } else {
                double[] fmagnitudeSpectrum = m_fft.calculateFFTMagnitude(fspeechFrame);
                for(int j=m_nboundariesDFTBins[i][0], k=0;j<=m_nboundariesDFTBins[i][1];j++,k++) {
                    dfilterOutput[i] += fmagnitudeSpectrum[j] * m_dweights[i][k];
                }
            }

            //ISIP (Mississipi univ.) implementation
            if (dfilterOutput[i] > m_dminimumFilterOutput) {//floor power to avoid log(0)
                dfilterOutput[i] = Math.log(dfilterOutput[i]); //using ln
            } else {
                dfilterOutput[i] = m_dlogFilterOutputFloor;
            }
        }
        return dfilterOutput;
    }

    public static void main(String[] args) {

        int nnumberofFilters = 24;
        int nlifteringCoefficient = 22;
        boolean oisLifteringEnabled = true;
        boolean oisZeroThCepstralCoefficientCalculated = false;
        int nnumberOfMFCCParameters = 12; //without considering 0-th
        double dsamplingFrequency = 8000.0;
        int nFFTLength = 512;
        if (oisZeroThCepstralCoefficientCalculated) {
            //take in account the zero-th MFCC
            nnumberOfMFCCParameters = nnumberOfMFCCParameters + 1;
        }
//          else { 
//            nnumberOfMFCCParameters = nnumberOfMFCCParameters; 
//          } 

        MFCC mfcc = new MFCC(nnumberOfMFCCParameters,
                dsamplingFrequency,
                nnumberofFilters,
                nFFTLength,
                oisLifteringEnabled,
                nlifteringCoefficient,
                oisZeroThCepstralCoefficientCalculated);
          MFCC.isDebugMode = true;

          debug(mfcc.toString()); 

        //simulate a frame of speech
        double[] x = new double[160];
        x[2]=10; x[4]=14;
        double[] dparameters = mfcc.getParameters(x);
          debug("MFCC parameters:"); 
        for (int i = 0; i < dparameters.length; i++) {
        	  debug(" " + dparameters[i]); 

        }

    }
    public static boolean isDebugMode = false;
    public static void debug(String str){
        if(isDebugMode){
            System.out.println("[" + MFCC.class.getSimpleName()+ "]"+str);
        }
    }


    /**Calculates the DFT using a split-radix Fast Fourier Transform algorithm.
     * @author Junan Zhang
     */
    public final static class FFT {
        public int logm;
        final  int  MAXLOGM=20;     /* max FFT length      2^MAXLOGM */
        final  double TWOPI=6.28318530717958647692;
        final  double SQHALF=0.707106781186547524401;
        int brseed[]= new int[4048];
        float tab[][];

        public FFT(int nlength ) {
            double dtemp = Math.log(nlength) / Math.log(2);
            if ( (dtemp - (int) dtemp) != 0.0) {
                throw new Error("FFT length must be a power of 2.");
            } else {
                this.logm = (int) dtemp;
            }
            if (logm>=4) {
                creattab(logm);
            }
        }

        /** Calculates the magnitude spectrum of a real signal.
         * The returned vector contains only the positive frequencies.
         */
        public float[] calculateFFTMagnitude(float x[]) {
            int i,n;
            n=1<<this.logm;

            if (x.length > n) {
                throw new Error("Tried to use a " + n + "-points FFT for a vector with " +
                        x.length + " samples!");
            }




            rsfft(x);

            float[] mag = new float[n/2 + 1];
            for (int j = 0; j < mag.length; j++) {
                mag[j]=0F;

            }
            mag[0] = x[0]; //DC frequency must be positive always

            if (n==1) {
                return mag;
            }
            mag[n/2] = (float) Math.abs(x[n/2]); //pi (meaning: fs / 2)

            //System.out.println("FFT before magnitude");
            //IO.DisplayVector(x);

            for (i=1;i<n/2;i++) {
                double xi = x[i];
                double xni = x[n-i];
                mag[i] = (float) Math.sqrt(xi*xi+xni*xni);
                //System.out.println(mag[i] + " " + x[i] + " " + x[n-i]);
            }

            //IO.DisplayVector(mag);
            return mag;
        }

        /** Calculates the magnitude spectrum of a real signal.
         * The returned vector contains only the positive frequencies.
         */
        public double[] calculateFFTMagnitude(double inputData[]) {
            int i,n;
            n=1<<this.logm;
            if (inputData.length > n) {
                throw new Error("Tried to use a " + n + "-points FFT for a vector with " +
                        inputData.length + " samples!");
            }

            //System.out.println("magnitude test");
            //double[] dtest = DSP.DFTMagnitude(inputData);
            //IO.DisplayVector(dtest);

            float[] x = new float[n];
            for (i=0; i<inputData.length; i++) {
                x[i] = (float) inputData[i];
            }

            rsfft(x);

            //System.out.println("FFT before magnitude");
            //IO.DisplayVector(x);

            double[] mag = new double[n/2 + 1];
            mag[0] = x[0]; //DC frequency must be positive always

            if (n==1) {
                return mag;
            }
            mag[n/2] = Math.abs(x[n/2]); //pi (meaning: fs / 2)

            for (i=1;i<n/2;i++) {
                double xi = x[i];
                double xni = x[n-i];
                mag[i] = (float) Math.sqrt(xi*xi+xni*xni);
                //System.out.println(mag[i] + " " + x[i] + " " + x[n-i]);
            }

            //IO.DisplayVector(mag);
            return mag;
        }

        /** Calculates the power (magnitude squared) spectrum of a real signal.
         * The returned vector contains only the positive frequencies.
         */
        public double[] calculateFFTPower(double inputData[]) {
            int i,n;
            n=1<<this.logm;

            //System.out.println("magnitude test");
            //double[] dtest = DSP.DFTMagnitude(inputData);
            //IO.DisplayVector(dtest);

            float[] x = new float[n];
            for (i=0; i<inputData.length; i++) {
                x[i] = (float) inputData[i];
            }

            rsfft(x);

            //System.out.println("FFT before magnitude");
            //IO.DisplayVector(x);

            double[] mag = new double[n/2 + 1];
            for (int j = 0; j < mag.length; j++) {
                mag[j] = 0F;

            }
            mag[0] = x[0]; //DC frequency must be positive always

            if (n==1) {
                return mag;
            }
            mag[n/2] = Math.abs(x[n/2]); //pi (meaning: fs / 2)

            for (i=1;i<n/2;i++) {
//			mag[i] = x[i]*x[i]+x[n-i]*x[n-i];
                double xi = x[i];
                double xni = x[n-i];
                mag[i] = (float) Math.sqrt(xi*xi+xni*xni);
                //mag[i] = Math.sqrt(x[i]*x[i]+x[n-i]*x[n-i]);
                //System.out.println(mag[i] + " " + x[i] + " " + x[n-i]);
            }

            //IO.DisplayVector(mag);
            return mag;
        }

        /**In place calculation of FFT magnitude.
         */
        public void FFTMagnitude(float x[])
        { int i,n;
            rsfft(x);
            n=1<<this.logm;
            if (n==1) return;
            for (i=1;i<n/2;i++)
            {x[i]=(float)Math.sqrt(x[i]*x[i]+x[n-i]*x[n-i]);
                x[n-i]=x[i];
            }
            //Aldebaro modification:
            x[n/2] = Math.abs(x[n/2]);
        }

        void rsfft(float x[])
        {
      /*creat table*/
            //  if(logm>=4) creattab(logm);
      /* Call recursive routine */

            rsrec(x,logm);

    /* Output array unshuffling using bit-reversed indices */
            if (logm > 1) {
                BR_permute(x, logm);
                return ;
            }
        }

/* -------------------------------------------------------------------- *
   *   Inverse  transform  for  real  inputs                              *
   *--------------------------------------------------------------------  */

        void  rsifft(float x[])
        {
            int       i, m;
            float     fac;
            int         xp;

   /* Output array unshuffling using bit-reversed indices */
            if (logm > 1) {
                BR_permute(x, logm);
            }
            x[0] *= 0.5;
            if (logm > 0) x[1] *= 0.5;
 /*creat table*/
            //if(logm>=4) creattab(logm);
   /*  Call  recursive  routine */

            rsirec(x, logm);

   /* Normalization */
            m = 1 << logm;
            fac = (float)2.0 / m;
            xp = 0;

            for (i = 0; i < m; i++) {
                x[xp++] *= fac;
            }
        }

/* -------------------------------------------------------------------- *
 *     Creat multiple fator table                                       *
 * -------------------------------------------------------------------- */

        void   creattab(int logm)
        { int       m, m2, m4, m8, nel, n, rlogm;
            int       cn, spcn, smcn, c3n, spc3n, smc3n;
            double    ang, s, c;
            tab=new float [logm-4+1][6*((1<<logm)/4-2)];
            for(rlogm=logm; rlogm>=4;rlogm--)

            {m = 1 << rlogm; m2 = m / 2; m4 = m2 / 2; m8 = m4 / 2; nel=m4-2;
 /* Initialize pointers */

                cn =0; spcn = cn + nel;  smcn = spcn + nel;c3n = smcn + nel; spc3n = c3n + nel; smc3n = spc3n + nel;


    /* Compute tables */
                for (n = 1; n < m4; n++) {
                    if (n == m8) continue;
                    ang = n * TWOPI / m;
                    c = Math.cos(ang);  s = Math.sin(ang);
                    tab[rlogm-4][cn++] = (float)c;  tab[rlogm-4][spcn++] = (float)(- (s + c)); tab[rlogm-4][smcn++] =(float)( s - c);

                    ang = 3 * n * TWOPI / m;
                    c = Math.cos(ang);  s = Math. sin(ang);
                    tab[rlogm-4][c3n++] = (float)c; tab[rlogm-4][spc3n++] = (float)(- (s + c)); tab[rlogm-4][smc3n++] = (float)(s - c);
                }
            }
        }

/* -------------------------------------------------------------------- *
 *     Recursive part of the RSFFT algorithm.       Not externally      *
 *     callable.                                                        *
 * -------------------------------------------------------------------- */

        void  rsrec(float x[],int logm)
        {
            int       m, m2, m4, m8, nel, n;
            int       x0=0;
            int       xr1, xr2, xi1;
            int       cn=0;
            int       spcn=0;
            int       smcn=0;
            float     tmp1, tmp2;
//	 double    ang, c, s;




     /* Check range   of logm */
            try{ if ((logm < 0) || (logm > MAXLOGM)) {
                System.err.println("FFT length m is too big: log2(m) = "+logm+"is out of bounds ["+0+","+MAXLOGM+"]");

                throw new OutofborderException(logm);
            }}
            catch( OutofborderException e)
            {throw new OutOfMemoryError();}

     /* Compute trivial cases */

            if (logm < 2) {
                if (logm == 1) {    /* length m = 2 */
                    xr2  = x0 + 1;
                    tmp1 = x[x0] + x[xr2];
                    x[xr2] = x[x0] - x[xr2];
                    x[x0]   =  tmp1;
                    return;
                }
                else if (logm == 0) return;      /* length m = 1 */
            }

     /* Compute a few constants */
            m = 1 << logm; m2 = m / 2; m4 = m2 / 2; m8 = m4 / 2;

     /* Build tables of butterfly coefficients, if necessary */
            //if ((logm >= 4) && (tab[logm-4][0] == 0)) {

         /* Allocate memory for tables */
            //  nel = m4 - 2;

    /*if ((tab[logm-4] = (float *) calloc(3 * nel, sizeof(float)))
       == NULL) {
       printf("Error : RSFFT : not enough memory for cosine tables.\n");
       error_exit();
    }*/


    /* Initialize pointers */
            //tabi=logm-4;
            //   cn  =0; spcn = cn + nel;  smcn = spcn + nel;

    /* Compute tables */
    /*for (n = 1; n < m4; n++) {
       if (n == m8) continue;
       ang = n * (float)TWOPI / m;
       c = Math.cos(ang);  s = Math.sin(ang);
       tab[tabi][cn++] = (float)c;  tab[tabi][spcn++] = (float)(- (s + c)); tab[tabi][smcn++] =(float)( s - c);
   }
}

/*  Step  1 */
            xr1 = x0;  xr2 = xr1 + m2;
            for (n = 0; n < m2; n++) {
                tmp1 = x[xr1] + x[xr2];
                x[xr2] = x[xr1] - x[xr2];
                x[xr1] = tmp1;
                xr1++; xr2++;
            }

/*  Step  2        */
            xr1 = x0 + m2 + m4;
            for (n = 0; n < m4; n++) {
                x[xr1] = - x[xr1];
                xr1++;
            }

/*  Steps 3 &  4 */
            xr1 = x0 + m2; xi1 = xr1 + m4;
            if (logm >= 4) {
                nel = m4 - 2;
                cn  = 0; spcn = cn + nel;  smcn = spcn + nel;
            }

            xr1++; xi1++;
            for (n = 1; n < m4; n++) {
                if (n == m8) {
                    tmp1 = (float)( SQHALF * (x[xr1] + x[xi1]));
                    x[xi1]  = (float)(SQHALF * (x[xi1] - x[xr1]));
                    x[xr1]  = tmp1;
                }  else {//System.out.println ("logm-4="+(logm-4));
                    tmp2 = tab[logm-4][cn++] * (x[xr1] + x[xi1]);
                    tmp1 = tab[logm-4][spcn++] * x[xr1] + tmp2;
                    x[xr1] = tab[logm-4][smcn++] * x[xi1] + tmp2;
                    x[xi1] = tmp1;
                }
                //System.out.println ("logm-4="+(logm-4));
                xr1++; xi1++;
            }

    /*  Call rsrec again with half DFT length */
            rsrec(x,logm-1);

    /* Call complex DFT routine, with quarter DFT length.
        Constants have to be recomputed, because they are static! */
            m = 1 << logm; m2 = m / 2; m4 = 3 * (m / 4);
            srrec(x,x0 + m2, x0 + m4, logm-2);

    /* Step 5: sign change & data reordering */
            m = 1 << logm; m2 = m / 2; m4 = m2 / 2; m8 = m4 / 2;
            xr1 = x0 + m2 + m4;
            xr2 = x0 + m - 1;
            for (n = 0; n < m8; n++) {
                tmp1   = x[xr1];
                x[xr1++] = - x[xr2];
                x[xr2--] = - tmp1;
            }
            xr1 = x0 + m2 + 1;
            xr2 = x0 + m - 2;
            for (n = 0; n < m8; n++) {
                tmp1   =   x[xr1];
                x[xr1++] = - x[xr2];
                x[xr2--] =   tmp1;
                xr1++;
                xr2--;
            }
            if (logm == 2) x[3] = -x[3];
        }
/* --------------------------------------------------------------------- *
 *  Recursive part of the inverse RSFFT algorithm.  Not externally       *
 *  callable.                                                            *
 *  -------------------------------------------------------------------- */

        void  rsirec(float  x[],  int   logm)
        {
            int       m, m2, m4, m8, nel, n;
            int       xr1, xr2, xi1;
            int       x0=0;
            int       cn, spcn, smcn;
            float     tmp1, tmp2;
            cn=0;smcn=0;spcn=0;

     /* Check  range  of logm */
            try{ if ((logm < 0) || (logm > MAXLOGM)) {
                System.err.println("FFT length m is too big: log2(m) = "+logm+"is out of bounds ["+0+","+MAXLOGM+"]");
                throw new OutofborderException(logm);
            }}
            catch( OutofborderException e)
            {throw new OutOfMemoryError();}

     /*  Compute  trivial  cases */
            if (logm < 2) {
                if (logm == 1) {     /* length m = 2 */
                    xr2  = x0 + 1;
                    tmp1 = x[x0] + x[xr2];
                    x[xr2] = x[x0] - x[xr2];
                    x[0]= tmp1;
                    return;
                }
                else if (logm == 0) return;       /* length m = 1 */
            }

     /* Compute a few constants */
            m = 1 << logm; m2 = m / 2; m4 = m2 / 2; m8 = m4 / 2;

     /* Build tables of butterfly    coefficients, if necessary */
            // if((logm >= 4) && (tab[logm-4] == NULL)) {

       /* Allocate memory for tables */
      /*el = m4 - 2;
       if ((tab[logm-4] = (float *) calloc(3 * nel, sizeof(float)))
           == NULL) {
           printf("Error : RSFFT : not enough memory for cosine tables.\n");
           error_exit();
       }

       /*  Initialize   pointers */
            //cn  = tab[logm-4] ; spcn = cn + nel; smcn = spcn + nel;

     /*  Compute  tables */
     /* (n = 1; n < m4; n++) {
         if (n == m8) continue;
         ang = n * TWOPI / m;
         c = cos(ang); s = sin(ang);
         *cn++ = c; *spcn++ = - (s + c); *smcn++ = s - c;
    }
}
 /* Reverse Step 5: sign change & data reordering */
            if (logm == 2) x[3] = -x[3];
            xr1 = x0+ m2 + 1;
            xr2 = x0+ m - 2;
            for (n = 0; n < m8; n++) {
                tmp1   =   x[xr1];
                x[xr1++] =   x[xr2];
                x[xr2--] = - tmp1;
                xr1++;
                xr2--;
            }
            xr1 = x0 + m2 + m4;
            xr2 = x0 + m - 1;
            for (n = 0; n < m8; n++) {
                tmp1   =   x[xr1];
                x[xr1++] = - x[xr2];
                x[xr2--] = - tmp1;
            }
 /*  Call   rsirec again with half DFT length */
            rsirec(x, logm-1);

 /* Call complex DFT routine, with quarter DFT length.
     Constants have to be recomputed, because they are static! */

 /*Now in Java version, we set the multiple Constant to be global*/
            m = 1 << logm; m2 = m / 2; m4 = 3 * (m / 4);
            srrec(x,x0 + m4, x0 + m2, logm-2);

 /* Reverse Steps 3 & 4 */
            m = 1 << logm; m2 = m / 2; m4 = m2 / 2; m8 = m4 / 2;
            xr1 = x0 + m2; xi1 = xr1 + m4;
            if (logm >= 4) {
                nel = m4 - 2;
                cn  = 0; spcn = cn + nel; smcn = spcn + nel;
            }
            xr1++; xi1++;
            for (n = 1; n < m4; n++) {
                if (n == m8) {
                    tmp1 = (float)(SQHALF * (x[xr1] - x[xi1]));
                    x[xi1] = (float)(SQHALF * (x[xi1] + x[xr1]));
                    x[xr1] = tmp1;
                }    else {
                    tmp2 = tab[logm-4][cn++] * (x[xr1] + x[xi1]);
                    tmp1 = tab[logm-4][smcn++] * x[xr1] + tmp2;
                    x[xr1] = tab[logm-4][spcn++] * x[xi1] + tmp2;
                    x[xi1] = tmp1;
                }
                xr1++; xi1++;
            }

 /* Reverse Step 2 */
            xr1 = x0 + m2 + m4;
            for (n = 0; n < m4; n++) {
                x[xr1] = - x[xr1];
                xr1++;
            }

 /* Reverse  Step  1 */
            xr1 = x0; xr2 = xr1 + m2;
            for (n = 0; n < m2; n++) {
                tmp1 = x[xr1] + x[xr2];
                x[xr2] = x[xr1] - x[xr2];
                x[xr1] = tmp1;
                xr1++; xr2++;
            }
        }


  /* -------------------------------------------------------------------- *
 *      Recursive part of the SRFFT algorithm.                          *
 * -------------------------------------------------------------------- */

        void srrec(float x[],int xr, int xi, int logm)
        {
            int        m, m2, m4, m8, nel, n;
            // int        x0=0;
            int        xr1, xr2, xi1, xi2;
            int        cn, spcn, smcn, c3n, spc3n, smc3n;
            float      tmp1, tmp2;
            cn=0; spcn=0; smcn=0; c3n=0; spc3n=0; smc3n=0;




/* Check range of logm */
            try
            {if ((logm < 0) || (logm > MAXLOGM)) {
                System.err.println("FFT length m is too big: log2(m) = "+logm+"is out of bounds ["+0+","+MAXLOGM+"]");

                throw new OutofborderException(logm) ;
            }
            }
            catch ( OutofborderException e)
            {throw new OutOfMemoryError();}

/*  Compute trivial cases */
            if (logm < 3) {
                if (logm == 2) {  /* length m = 4 */
                    xr2  = xr + 2;
                    xi2  = xi + 2;
                    tmp1 = x[xr] + x[xr2];
                    x[xr2] = x[xr] - x[xr2];
                    x[xr]  = tmp1;
                    tmp1 = x[xi] + x[xi2];
                    x[xi2] = x[xi] - x[xi2];
                    x[xi]  = tmp1;
                    xr1  = xr + 1;
                    xi1  = xi + 1;
                    xr2++;
                    xi2++;
                    tmp1 = x[xr1] + x[xr2];
                    x[xr2] = x[xr1] - x[xr2];
                    x[xr1] = tmp1;
                    tmp1 = x[xi1] + x[xi2];
                    x[xi2] = x[xi1] - x[xi2];
                    x[xi1] = tmp1;
                    xr2  = xr + 1;
                    xi2  = xi + 1;
                    tmp1 = x[xr] + x[xr2];
                    x[xr2] = x[xr] - x[xr2];
                    x[xr]  = tmp1;
                    tmp1 = x[xi] + x[xi2];
                    x[xi2] = x[xi] - x[xi2];
                    x[xi]  = tmp1;
                    xr1  = xr + 2;
                    xi1  = xi + 2;
                    xr2  = xr + 3;
                    xi2  = xi + 3;
                    tmp1 = x[xr1] + x[xi2];
                    tmp2 = x[xi1] + x[xr2];
                    x[xi1] = x[xi1] - x[xr2];
                    x[xr2] = x[xr1] - x[xi2];
                    x[xr1] =tmp1;
                    x[xi2] =tmp2;
                    return;
                }

                else  if (logm == 1) { /* length m = 2 */
                    xr2   = xr +  1;
                    xi2   = xi +  1;
                    tmp1  = x[xr] + x[xr2];
                    x[xr2]  = x[xr] - x[xr2];
                    x[xr]   = tmp1;
                    tmp1  = x[xi] + x[xi2];
                    x[xi2]  = x[xi] - x[xi2];
                    x[xi]   = tmp1;
                    return;
                }
                else if (logm == 0) return;     /* length m = 1*/
            }

/* Compute a few constants */
            m = 1 << logm; m2 = m / 2; m4 = m2 / 2; m8 = m4 / 2;

/* Build tables of butterfly coefficients, if necessary */
//if ((logm >= 4) && (tab[logm-4] == NULL)) {

    /* Allocate memory for tables */
  /*  nel = m4 - 2;
    if ((tab[logm-4] = (float *) calloc(6 * nel, sizeof(float)))
       == NULL) {
       exit(1);
     }
    /* Initialize pointers */

    /*cn  = tab[logm-4]; spcn = cn + nel;  smcn = spcn + nel;
    c3n = smcn + nel; spc3n = c3n + nel; smc3n = spc3n + nel;


    /* Compute tables */
    /*for (n = 1; n < m4; n++) {
       if (n == m8) continue;
       ang = n * TWOPI / m;
       c = cos(ang); s = sin(ang);
       *cn++ = c; *spcn++ = - (s + c); *smcn++ = s - c;
       ang = 3 * n * TWOPI / m;
       c = cos(ang); s = sin(ang);
       *c3n++ = c; *spc3n++ = - (s + c); *smc3n++ = s - c;
   }
}


/*  Step 1 */
            xr1 = xr;  xr2 = xr1  +  m2;
            xi1 = xi;  xi2 = xi1  +  m2;

            for (n = 0; n < m2; n++) {
                tmp1 = x[xr1] + x[xr2];
                x[xr2] = x[xr1] - x[xr2];
                x[xr1] = tmp1;
                tmp2 = x[xi1] + x[xi2];
                x[xi2] = x[xi1] - x[xi2];
                x[xi1] = tmp2;
                xr1++;  xr2++;  xi1++;  xi2++;
            }
/*   Step 2  */
            xr1 = xr + m2; xr2 = xr1 + m4;
            xi1 = xi + m2; xi2 = xi1 + m4;
            for (n = 0; n < m4; n++) {
                tmp1 = x[xr1] + x[xi2];
                tmp2 = x[xi1] + x[xr2];
                x[xi1] = x[xi1] - x[xr2];
                x[xr2] = x[xr1] - x[xi2];
                x[xr1] = tmp1;
                x[xi2] = tmp2;
                xr1++;  xr2++;  xi1++;  xi2++;
            }

/*   Steps  3 & 4 */
            xr1 = xr + m2; xr2 = xr1 + m4;
            xi1 = xi + m2; xi2 = xi1 + m4;
            if (logm >= 4) {
                nel = m4 - 2;
                cn  = 0; spcn  = cn + nel;  smcn  = spcn + nel;
                c3n = smcn + nel;  spc3n = c3n + nel; smc3n = spc3n + nel;
            }
            xr1++; xr2++; xi1++; xi2++;
            for (n = 1; n < m4; n++) {
                if (n == m8) {
                    tmp1 = (float)(SQHALF * (x[xr1] + x[xi1]));
                    x[xi1] = (float)(SQHALF * (x[xi1] - x[xr1]));
                    x[xr1] = tmp1;
                    tmp2 = (float)(SQHALF * (x[xi2] - x[xr2]));
                    x[xi2] = (float)(-SQHALF * (x[xr2] + x[xi2]));
                    x[xr2] = tmp2;
                }     else {
                    tmp2 = tab[logm-4][cn++] * (x[xr1] + x[xi1]);
                    tmp1 = tab[logm-4][spcn++] * x[xr1] + tmp2;
                    x[xr1] = tab[logm-4][smcn++] * x[xi1] + tmp2;
                    x[xi1] = tmp1;
                    tmp2 = tab[logm-4][c3n++] * (x[xr2] + x[xi2]);
                    tmp1 = tab[logm-4][spc3n++] * x[xr2] + tmp2;
                    x[xr2] = tab[logm-4][smc3n++] * x[xi2] + tmp2;
                    x[xi2] = tmp1;
                }
                // System.out.println ("logm-4="+(logm-4));
                xr1++; xr2++; xi1++; xi2++;
            }
   /* Call ssrec again with half DFT length  */
            srrec(x,xr, xi, logm-1);

   /* Call ssrec again twice with one quarter DFT length.
     Constants have to be recomputed, because they are static!*/
            m = 1 << logm; m2 = m / 2;
            srrec(x,xr + m2, xi + m2, logm-2);
            m = 1 << logm; m4 = 3 * (m / 4);
            srrec(x,xr + m4, xi + m4, logm-2);
        }

/* -------------------------------------------------------------------- *
 *    Data unshuffling according to bit-reversed indexing.              *
 *                                                                      *
 *                                                                      *
 *    Bit reversal is done using Evan's algorithm (Ref: D. M. W.        *
 *    Evans, "An improved digit-reversal permutation algorithm...",     *
 *    IEEE Trans.  ASSP, Aug. 1987, pp. 1120-1125).                     *
 * -------------------------------------------------------------------- */

//static    int   brseed[256];     /* Evans' seed table */
//static    int     brsflg;         /* flag for table building */


        void creatbrseed( int logm)
        {int lg2,n;
            lg2 = logm >> 1;
            n =1 << lg2;
            if (logm!=(logm>>1)<<1) lg2++;
            brseed[0] = 0;
            brseed[1] = 1;
            for  (int j=2; j <= lg2; j++) {
                int imax = 1 << (j-1);
                for (int i = 0; i < imax; i++) {
                    brseed[i] <<= 1;
                    brseed[i + imax] = brseed[i] + 1;
                }
            }
        }
        void BR_permute(float x[], int logm)
        {
            int       i, j, lg2, n;
            int       off, fj, gno;
            float     tmp;
            int       xp, xq, brp;
            int       x0=0;

            lg2 =  logm >> 1;
            n = 1  << lg2;
            if  (logm !=(logm>>1)<<1) lg2++;

   /*  Create seed table if not yet built */
  /* if  (brsflg != logm) {
       brsflg = logm;
       brseed[0] = 0;
       brseed[1] = 1;
       for  (j=2; j <= lg2; j++) {
          imax = 1 << (j-1);
          for (i = 0; i < imax; i++) {
             brseed[i] <<= 1;
             brseed[i + imax] = brseed[i] + 1;
          }
       }
   }*/
            creatbrseed(logm);

    /*  Unshuffling   loop */
            for (off = 1; off < n; off++) {
                fj = n * brseed[off]; i = off; j = fj;
                tmp = x[i]; x[i] = x[j]; x[j] = tmp;
                xp = i;
                brp = 1;

                for (gno = 1; gno < brseed[off]; gno++) {
                    xp += n;
                    j = fj + brseed[brp++];
                    xq = x0 + j;
                    tmp = x[xp]; x[xp] = x[xq]; x[xq] = tmp;
                }
            }

        }
        class OutofborderException extends Exception {
            /**
             *
             */
            private static final long serialVersionUID = 1L;

            public OutofborderException (int logm)
            {super();
            }
        }
    }


}