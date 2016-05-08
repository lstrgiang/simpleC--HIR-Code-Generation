int fact(int n) {
	int f;
	if(n == 0){
		return 1;
	}
	f = fact(n - 1);
	return f * n;
}
void main(){
	int n;
	int h;
	cout << "Input n: ";
	cin >> n;
	for(h=0;h<3;h=h+1){
		cout<<"Hello world\n";
	}
	cout << n << "! = " << fact(n)<<"\n";
}
