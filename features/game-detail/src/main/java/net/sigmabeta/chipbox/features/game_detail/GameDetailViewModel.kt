package net.sigmabeta.chipbox.features.game_detail

//@HiltViewModel
//class GameDetailViewModel @Inject constructor(
//    private val repository: Repository
//): ViewModel() {
//    private val _game = MutableLiveData<List<Game>>()
//    val game: LiveData<List<Game>> = _game
//
//    init {
//        viewModelScope.launch {
//            val game = repository.getAllGameDetail()
//            onGameDetailLoaded(game)
//        }
//    }
//
//    override fun onCleared() {
//        viewModelScope.cancel()
//    }
//
//    private fun onGameDetailLoaded(game: List<Game>) {
//        _game.value = game
//    }
//}